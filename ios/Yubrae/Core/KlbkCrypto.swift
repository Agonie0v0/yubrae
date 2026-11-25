import CommonCrypto
import CryptoKit
import Foundation
import Security

public enum KlbkCryptoError: LocalizedError, Equatable {
    case blankPassphrase
    case invalidHeader
    case truncatedData
    case randomGenerationFailed(OSStatus)
    case keyDerivationFailed(Int32)
    case invalidParameterLength
    case authenticationFailed
    case invalidUTF8

    public var errorDescription: String? {
        switch self {
        case .blankPassphrase:
            return "备份口令不能为空"
        case .invalidHeader:
            return "无效的备份文件格式：文件头不是 KLBK1"
        case .truncatedData:
            return "备份文件数据不完整或已损坏"
        case .randomGenerationFailed(let status):
            return "安全随机数生成失败（status=\(status)）"
        case .keyDerivationFailed(let status):
            return "密钥派生失败（status=\(status)）"
        case .invalidParameterLength:
            return "Salt 或 IV 长度无效（需 16B Salt / 12B IV）"
        case .authenticationFailed:
            return "解密失败：备份口令错误或文件已被篡改损坏"
        case .invalidUTF8:
            return "解密成功但内容不是有效的 UTF-8 文本"
        }
    }
}

/// 棱账 / 余白 `.klbk` 备份加解密引擎（100% 与 Android `CryptoHelper.kt` 字节级双向互通）。
///
/// 二进制布局：
/// `[5 bytes Magic "KLBK1"] [16 bytes Salt] [12 bytes IV] [Ciphertext + 16 bytes GCM Tag]`
///
/// - 密钥派生：`PBKDF2-HMAC-SHA256`（120,000 iterations, 256-bit / 32-byte key）
/// - 对称加密：`AES-256-GCM`（128-bit / 16-byte Tag，AAD = ASCII `"KLBK1"`）
public enum KlbkCrypto {
    public static let magicString = "KLBK1"
    public static let magicData = Data(magicString.utf8) // 5 bytes: 0x4B 4C 42 4B 31

    public static let pbkdf2Iterations: UInt32 = 120_000
    public static let saltLength = 16
    public static let ivLength = 12
    public static let keyLength = 32
    public static let gcmTagLength = 16
    public static let minimumEncryptedLength = magicData.count + saltLength + ivLength + gcmTagLength

    /// 加密二进制明文为 `.klbk` 格式。
    public static func encrypt(plaintext: Data, passphrase: String) throws -> Data {
        guard !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw KlbkCryptoError.blankPassphrase
        }
        let salt = try randomBytes(count: saltLength)
        let iv = try randomBytes(count: ivLength)
        return try encrypt(plaintext: plaintext, passphrase: passphrase, salt: salt, iv: iv)
    }

    /// 指定固定 Salt (16B) 与 IV (12B) 加密，供跨端一致性校验与单元测试使用。
    public static func encrypt(
        plaintext: Data,
        passphrase: String,
        salt: Data,
        iv: Data
    ) throws -> Data {
        guard !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw KlbkCryptoError.blankPassphrase
        }
        guard salt.count == saltLength, iv.count == ivLength else {
            throw KlbkCryptoError.invalidParameterLength
        }

        var keyData = try deriveKey(passphrase: passphrase, salt: salt)
        defer { keyData.resetBytes(in: 0..<keyData.count) }

        let symmetricKey = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: iv)
        let sealedBox = try AES.GCM.seal(
            plaintext,
            using: symmetricKey,
            nonce: nonce,
            authenticating: magicData
        )

        var output = Data(capacity: magicData.count + saltLength + ivLength + sealedBox.ciphertext.count + gcmTagLength)
        output.append(magicData)
        output.append(salt)
        output.append(iv)
        output.append(sealedBox.ciphertext)
        output.append(sealedBox.tag)
        return output
    }

    /// 解密 `.klbk` 二进制数据为原始明文。
    public static func decrypt(encryptedData: Data, passphrase: String) throws -> Data {
        guard !passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw KlbkCryptoError.blankPassphrase
        }

        // 重置 Data 切片索引到 0，避免 SubSequence startIndex 非零导致的越界问题
        let raw = Data(encryptedData)
        guard raw.count >= magicData.count else {
            throw KlbkCryptoError.truncatedData
        }
        guard raw.prefix(magicData.count) == magicData else {
            throw KlbkCryptoError.invalidHeader
        }
        guard raw.count >= minimumEncryptedLength else {
            throw KlbkCryptoError.truncatedData
        }

        let saltStart = magicData.count
        let ivStart = saltStart + saltLength
        let cipherStart = ivStart + ivLength
        let tagStart = raw.count - gcmTagLength

        let salt = Data(raw[saltStart..<ivStart])
        let iv = Data(raw[ivStart..<cipherStart])
        let ciphertext = Data(raw[cipherStart..<tagStart])
        let tag = Data(raw[tagStart..<raw.count])

        var keyData = try deriveKey(passphrase: passphrase, salt: salt)
        defer { keyData.resetBytes(in: 0..<keyData.count) }

        do {
            let symmetricKey = SymmetricKey(data: keyData)
            let nonce = try AES.GCM.Nonce(data: iv)
            let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
            return try AES.GCM.open(sealedBox, using: symmetricKey, authenticating: magicData)
        } catch {
            throw KlbkCryptoError.authenticationFailed
        }
    }

    /// 加密 UTF-8 字符串为 `.klbk` 数据。
    public static func encryptString(_ plaintext: String, passphrase: String) throws -> Data {
        try encrypt(plaintext: Data(plaintext.utf8), passphrase: passphrase)
    }

    /// 解密 `.klbk` 数据为 UTF-8 字符串。
    public static func decryptToString(_ encryptedData: Data, passphrase: String) throws -> String {
        let data = try decrypt(encryptedData: encryptedData, passphrase: passphrase)
        guard let str = String(data: data, encoding: .utf8) else {
            throw KlbkCryptoError.invalidUTF8
        }
        return str
    }

    /// 使用 CommonCrypto PBKDF2-HMAC-SHA256 派生 256-bit 密钥。
    public static func deriveKey(passphrase: String, salt: Data, iterations: UInt32 = pbkdf2Iterations) throws -> Data {
        guard salt.count == saltLength else {
            throw KlbkCryptoError.invalidParameterLength
        }
        let passwordData = Data(passphrase.utf8)
        var derivedKey = Data(count: keyLength)

        let status: Int32 = derivedKey.withUnsafeMutableBytes { derivedRaw in
            let derivedPtr = derivedRaw.bindMemory(to: UInt8.self).baseAddress
            return passwordData.withUnsafeBytes { passwordRaw in
                let passwordPtr = passwordRaw.bindMemory(to: Int8.self).baseAddress
                return salt.withUnsafeBytes { saltRaw in
                    let saltPtr = saltRaw.bindMemory(to: UInt8.self).baseAddress
                    return CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordPtr,
                        passwordData.count,
                        saltPtr,
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        iterations,
                        derivedPtr,
                        keyLength
                    )
                }
            }
        }

        guard status == CCCryptorStatus(kCCSuccess) else {
            throw KlbkCryptoError.keyDerivationFailed(status)
        }
        return derivedKey
    }

    private static func randomBytes(count: Int) throws -> Data {
        var bytes = Data(count: count)
        let status = bytes.withUnsafeMutableBytes { rawBuffer -> OSStatus in
            guard let ptr = rawBuffer.baseAddress else { return errSecAllocate }
            return SecRandomCopyBytes(kSecRandomDefault, count, ptr)
        }
        guard status == errSecSuccess else {
            throw KlbkCryptoError.randomGenerationFailed(status)
        }
        return bytes
    }
}
