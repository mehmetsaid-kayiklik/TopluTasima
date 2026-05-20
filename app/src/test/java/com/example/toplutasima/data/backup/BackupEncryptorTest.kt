package com.example.toplutasima.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Arrays

class BackupEncryptorTest {

    @Test
    fun testEncryptDecrypt_Success() {
        val originalText = "Sensitive Profile Backup Data Plaintext!"
        val plaintext = originalText.toByteArray(Charsets.UTF_8)
        val password = "SuperSecretPassword123".toCharArray()

        // 1. Encrypt
        val envelope = BackupEncryptor.encrypt(plaintext, password)
        assertNotNull(envelope)
        assertTrue(envelope.size > 28) // Salt (16) + IV (12) + ciphertext

        // 2. Decrypt with correct password
        val decryptedBytes = BackupEncryptor.decrypt(envelope, password)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testDecrypt_WithWrongPassword_ThrowsException() {
        val originalText = "Sensitive Profile Backup Data Plaintext!"
        val plaintext = originalText.toByteArray(Charsets.UTF_8)
        val correctPassword = "SuperSecretPassword123".toCharArray()
        val wrongPassword = "WrongPassword123".toCharArray()

        // Encrypt
        val envelope = BackupEncryptor.encrypt(plaintext, correctPassword)

        // Decrypt with wrong password should fail (AEAD tag mismatch or decryption error)
        try {
            BackupEncryptor.decrypt(envelope, wrongPassword)
            fail("Expected GeneralSecurityException but decryption succeeded")
        } catch (e: GeneralSecurityException) {
            // Expected exception
        }
    }

    @Test
    fun testDecrypt_TooShortEnvelope_ThrowsException() {
        val wrongEnvelope = ByteArray(10) // less than 28 bytes (16 salt + 12 IV)
        val password = "Password".toCharArray()

        try {
            BackupEncryptor.decrypt(wrongEnvelope, password)
            fail("Expected IllegalArgumentException for small envelope")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun testDecrypt_CorruptEnvelope_ThrowsException() {
        val originalText = "Sensitive Profile Backup Data Plaintext!"
        val plaintext = originalText.toByteArray(Charsets.UTF_8)
        val password = "SuperSecretPassword123".toCharArray()

        val envelope = BackupEncryptor.encrypt(plaintext, password)
        // Corrupt one byte of ciphertext/tag
        envelope[envelope.size - 1] = (envelope[envelope.size - 1] + 1).toByte()

        try {
            BackupEncryptor.decrypt(envelope, password)
            fail("Expected GeneralSecurityException for corrupted envelope")
        } catch (e: GeneralSecurityException) {
            // Expected due to GCM authentication tag validation failure
        }
    }
}
