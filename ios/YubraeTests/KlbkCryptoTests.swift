import Foundation
import XCTest
@testable import Yubrae

final class KlbkCryptoTests: XCTestCase {

    // 与 Android CryptoHelper.kt（JVM javax.crypto: PBKDF2WithHmacSHA256 120,000 轮 + AES/GCM/NoPadding + AAD "KLBK1"）
    // 实测生成的黄金跨端互测向量
    private let goldenPassphrase = "Yubrae#CrossPlatform2026!"
    private let goldenPlaintext = #"{"appName":"Yubrae","note":"余白跨端加密互通测试"}"#
    private let goldenSalt = Data((1...16).map { UInt8($0) })
    private let goldenIV = Data((0..<12).map { UInt8(0x20 + $0) })
    private let goldenDerivedKeyBase64 = "4fEDWpJeARbzHbVPgF3iy3gLj2toZjTGWVhIw+BrsYs="
    private let goldenAndroidKlbkBase64 = "S0xCSzEBAgMEBQYHCAkKCwwNDg8QICEiIyQlJicoKSorfejwlR6ciLIsVVhFjvBQJY28UMLEkfNgECG2khzbXkyYvJeROz2ihKwZuI7gN2yjGb5q8R8CL/mxbPjSCVnYJTQfuKHbjCOgF3aqIg=="

    func testRoundTripStringAndBinaryLayout() throws {
        let plaintext = #"{"formatVersion":3,"appName":"Yubrae","memo":"星巴克拿铁 ¥38.50 ☕️"}"#
        let passphrase = "StrongBackupPassword!2026"

        let encrypted1 = try KlbkCrypto.encryptString(plaintext, passphrase: passphrase)
        let encrypted2 = try KlbkCrypto.encryptString(plaintext, passphrase: passphrase)

        // 验证 5B Magic Header "KLBK1"
        XCTAssertEqual(encrypted1.prefix(5), Data("KLBK1".utf8))
        XCTAssertEqual(Array(encrypted1.prefix(5)), [0x4B, 0x4C, 0x42, 0x4B, 0x31])

        // 验证精确总长度：[5B Magic][16B Salt][12B IV][Ciphertext][16B GCM Tag]
        let expectedLen = 5 + 16 + 12 + Data(plaintext.utf8).count + 16
        XCTAssertEqual(encrypted1.count, expectedLen)

        // 每次随机 Salt/IV，产物必不相同
        XCTAssertNotEqual(encrypted1, encrypted2)

        // 解密往返精确还原
        let decrypted1 = try KlbkCrypto.decryptToString(encrypted1, passphrase: passphrase)
        let decrypted2 = try KlbkCrypto.decryptToString(encrypted2, passphrase: passphrase)
        XCTAssertEqual(decrypted1, plaintext)
        XCTAssertEqual(decrypted2, plaintext)
    }

    func testEmptyPlaintextRoundTrip() throws {
        let encrypted = try KlbkCrypto.encrypt(plaintext: Data(), passphrase: "empty-ok")
        XCTAssertEqual(encrypted.count, KlbkCrypto.minimumEncryptedLength)
        let decrypted = try KlbkCrypto.decrypt(encryptedData: encrypted, passphrase: "empty-ok")
        XCTAssertTrue(decrypted.isEmpty)
    }

    func testCrossPlatformGoldenVectorMatchesAndroidJvmByteForByte() throws {
        // 1. 验证 PBKDF2-HMAC-SHA256 120,000 轮派生密钥与 JVM 逐字节一致
        let derivedKey = try KlbkCrypto.deriveKey(passphrase: goldenPassphrase, salt: goldenSalt)
        XCTAssertEqual(derivedKey.count, 32)
        XCTAssertEqual(derivedKey.base64EncodedString(), goldenDerivedKeyBase64)

        // 2. 验证 Swift 固定 Salt/IV 加密产物与 Android CryptoHelper.kt 逐字节一致
        let swiftEncrypted = try KlbkCrypto.encrypt(
            plaintext: Data(goldenPlaintext.utf8),
            passphrase: goldenPassphrase,
            salt: goldenSalt,
            iv: goldenIV
        )
        XCTAssertEqual(swiftEncrypted.base64EncodedString(), goldenAndroidKlbkBase64)

        // 3. 验证直接解密 Android 端产出的 KLBK1 二进制数据
        let androidBytes = try XCTUnwrap(Data(base64Encoded: goldenAndroidKlbkBase64))
        let decoded = try KlbkCrypto.decryptToString(androidBytes, passphrase: goldenPassphrase)
        XCTAssertEqual(decoded, goldenPlaintext)
    }

    func testWrongPassphraseFailsWithAuthenticationError() throws {
        let encrypted = try KlbkCrypto.encryptString("secret ledger data", passphrase: "correct-password")
        XCTAssertThrowsError(try KlbkCrypto.decryptToString(encrypted, passphrase: "wrong-password")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .authenticationFailed)
        }
    }

    func testTamperedCiphertextOrTagOrHeaderFails() throws {
        let encrypted = try KlbkCrypto.encryptString("integrity check payload 123456", passphrase: "pass123")

        // 篡改密文字节
        var tamperedCipher = encrypted
        tamperedCipher[35] ^= 0x01
        XCTAssertThrowsError(try KlbkCrypto.decrypt(encryptedData: tamperedCipher, passphrase: "pass123")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .authenticationFailed)
        }

        // 篡改 GCM Tag 末字节
        var tamperedTag = encrypted
        tamperedTag[tamperedTag.count - 1] ^= 0x80
        XCTAssertThrowsError(try KlbkCrypto.decrypt(encryptedData: tamperedTag, passphrase: "pass123")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .authenticationFailed)
        }

        // 篡改文件头
        var tamperedHeader = encrypted
        tamperedHeader[4] = UInt8(ascii: "2") // "KLBK2"
        XCTAssertThrowsError(try KlbkCrypto.decrypt(encryptedData: tamperedHeader, passphrase: "pass123")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .invalidHeader)
        }
    }

    func testBlankPassphraseAndTruncatedDataErrors() {
        XCTAssertThrowsError(try KlbkCrypto.encryptString("abc", passphrase: "   ")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .blankPassphrase)
        }
        XCTAssertThrowsError(try KlbkCrypto.decrypt(encryptedData: Data("KLBK1".utf8), passphrase: "abc")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .truncatedData)
        }
        XCTAssertThrowsError(try KlbkCrypto.decrypt(encryptedData: Data("BAD".utf8), passphrase: "abc")) { error in
            XCTAssertEqual(error as? KlbkCryptoError, .truncatedData)
        }
    }
}
