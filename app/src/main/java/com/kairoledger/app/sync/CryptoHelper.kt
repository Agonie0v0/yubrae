package com.kairoledger.app.sync

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 棱账备份加解密工具。
 *
 * 二进制格式：
 * `[5 bytes Magic "KLBK1"] [16 bytes Salt] [12 bytes IV] [Ciphertext + 16 bytes GCM Tag]`
 *
 * 密钥派生：PBKDF2WithHmacSHA256 (120,000 iterations, 256-bit key)
 * 对称加密：AES/GCM/NoPadding (128-bit authentication tag, Magic Header 作为 AAD)
 */
object CryptoHelper {

    const val MAGIC_STRING = "KLBK1"
    private val MAGIC_BYTES = MAGIC_STRING.toByteArray(Charsets.US_ASCII)

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank()) { "加密口令不能为空" }

        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        val keyBytes = deriveKeyBytes(passphrase, salt)

        return try {
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(MAGIC_BYTES)
            val cipherText = cipher.doFinal(plaintext)

            ByteArray(MAGIC_BYTES.size + SALT_BYTES + IV_BYTES + cipherText.size).also { out ->
                var offset = 0
                System.arraycopy(MAGIC_BYTES, 0, out, offset, MAGIC_BYTES.size)
                offset += MAGIC_BYTES.size
                System.arraycopy(salt, 0, out, offset, SALT_BYTES)
                offset += SALT_BYTES
                System.arraycopy(iv, 0, out, offset, IV_BYTES)
                offset += IV_BYTES
                System.arraycopy(cipherText, 0, out, offset, cipherText.size)
            }
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    fun decrypt(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank()) { "解密口令不能为空" }

        if (encryptedBytes.size < MAGIC_BYTES.size) {
            throw IllegalArgumentException("无效的备份文件：文件长度过短，缺少 KLBK1 文件头")
        }
        for (i in MAGIC_BYTES.indices) {
            if (encryptedBytes[i] != MAGIC_BYTES[i]) {
                throw IllegalArgumentException("无效的备份文件格式：文件头不是 KLBK1")
            }
        }
        val minLength = MAGIC_BYTES.size + SALT_BYTES + IV_BYTES + GCM_TAG_BYTES
        if (encryptedBytes.size < minLength) {
            throw IllegalArgumentException("备份文件数据不完整或已损坏")
        }

        var offset = MAGIC_BYTES.size
        val salt = encryptedBytes.copyOfRange(offset, offset + SALT_BYTES)
        offset += SALT_BYTES
        val iv = encryptedBytes.copyOfRange(offset, offset + IV_BYTES)
        offset += IV_BYTES
        val cipherText = encryptedBytes.copyOfRange(offset, encryptedBytes.size)

        val keyBytes = deriveKeyBytes(passphrase, salt)
        return try {
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(MAGIC_BYTES)
            cipher.doFinal(cipherText)
        } catch (e: AEADBadTagException) {
            throw IllegalArgumentException("解密失败：备份口令错误或文件已被篡改损坏", e)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("解密失败：密钥校验异常或备份数据损坏（${e.message ?: "安全错误"}）", e)
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    fun encryptString(plaintext: String, passphrase: String): ByteArray =
        encrypt(plaintext.toByteArray(Charsets.UTF_8), passphrase)

    fun decryptToString(encryptedBytes: ByteArray, passphrase: String): String =
        decrypt(encryptedBytes, passphrase).toString(Charsets.UTF_8)

    private fun deriveKeyBytes(passphrase: String, salt: ByteArray): ByteArray {
        val chars = passphrase.toCharArray()
        val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            Arrays.fill(chars, '\u0000')
        }
    }
}
