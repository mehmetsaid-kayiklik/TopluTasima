package com.example.toplutasima.data.backup

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEncryptor {

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val ITERATION_COUNT = 210000
    private const val KEY_SIZE = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val GCM_TAG_LENGTH = 128

    /**
     * Encrypts plaintext bytes using AES/GCM with a key derived from password using PBKDF2.
     * Memory-safe: plaintext and password array should be cleared by the caller after use, or inside this function.
     */
    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE)
        random.nextBytes(salt)

        val iv = ByteArray(IV_SIZE)
        random.nextBytes(iv)

        val pbeSpec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_SIZE)
        var secretKeySpec: SecretKeySpec? = null
        try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val tempKey = factory.generateSecret(pbeSpec)
            val encodedKey = tempKey.encoded
            secretKeySpec = SecretKeySpec(encodedKey, "AES")
            Arrays.fill(encodedKey, 0.toByte()) // immediately clear temporary key encoding
        } finally {
            pbeSpec.clearPassword()
        }

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, spec)

        val ciphertext = cipher.doFinal(plaintext)

        // Envelope structure: [Salt (16 bytes)] + [IV (12 bytes)] + [Ciphertext]
        val output = ByteArray(SALT_SIZE + IV_SIZE + ciphertext.size)
        System.arraycopy(salt, 0, output, 0, SALT_SIZE)
        System.arraycopy(iv, 0, output, SALT_SIZE, IV_SIZE)
        System.arraycopy(ciphertext, 0, output, SALT_SIZE + IV_SIZE, ciphertext.size)

        return output
    }

    /**
     * Decrypts an envelope using AES/GCM with a key derived from password using PBKDF2.
     */
    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        if (envelope.size < SALT_SIZE + IV_SIZE) {
            throw IllegalArgumentException("Bozuk yedek dosyası (Dosya boyutu çok küçük)")
        }

        val salt = ByteArray(SALT_SIZE)
        val iv = ByteArray(IV_SIZE)
        System.arraycopy(envelope, 0, salt, 0, SALT_SIZE)
        System.arraycopy(envelope, SALT_SIZE, iv, 0, IV_SIZE)

        val ciphertextLength = envelope.size - SALT_SIZE - IV_SIZE
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(envelope, SALT_SIZE + IV_SIZE, ciphertext, 0, ciphertextLength)

        val pbeSpec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_SIZE)
        var secretKeySpec: SecretKeySpec? = null
        try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val tempKey = factory.generateSecret(pbeSpec)
            val encodedKey = tempKey.encoded
            secretKeySpec = SecretKeySpec(encodedKey, "AES")
            Arrays.fill(encodedKey, 0.toByte()) // immediately clear temporary key encoding
        } finally {
            pbeSpec.clearPassword()
        }

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, spec)

        return cipher.doFinal(ciphertext)
    }
}
