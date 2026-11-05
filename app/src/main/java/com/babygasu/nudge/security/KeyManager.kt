package com.babygasu.nudge.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.babygasu.nudge.data.NudgeDatabase
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keys live in the Android Keystore and never leave it.
 *  - The database passphrase is random, stored only in encrypted form (AES-GCM, Keystore key)
 *    in no-backup storage.
 *  - Sender identities are turned into keyed hashes (HMAC) so duplicate detection works
 *    without storing who sent a message.
 */
class KeyManager(private val context: Context) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val passphraseFile: File get() = File(context.noBackupFilesDir, "db.key.enc")

    @Synchronized
    fun databasePassphrase(): ByteArray {
        val file = passphraseFile
        if (file.exists()) {
            try {
                return decrypt(file.readBytes())
            } catch (e: Exception) {
                // The Keystore key is gone or invalid (e.g. device security reset): the old database
                // can never be opened again, so start fresh rather than crash.
                SafeLog.error("keys", "db_key_unreadable", e)
                file.delete()
            }
        }
        context.deleteDatabase(NudgeDatabase.NAME)
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.writeBytes(encrypt(passphrase))
        return passphrase
    }

    fun senderKey(packageName: String, senderName: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        val digest = mac.doFinal("$packageName|${senderName.trim().lowercase()}".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /** Makes all previously stored sender pseudonyms meaningless. */
    fun rotateSenderKey() {
        runCatching { keyStore.deleteEntry(HMAC_ALIAS) }
    }

    private fun wrappingKey(): SecretKey {
        (keyStore.getKey(DB_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(DB_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun hmacKey(): SecretKey {
        (keyStore.getKey(HMAC_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(HMAC_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + sealed
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val ivLength = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val sealed = blob.copyOfRange(1 + ivLength, blob.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(sealed)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DB_ALIAS = "nudge_db_wrap"
        const val HMAC_ALIAS = "nudge_sender_hmac"
        const val AES_GCM = "AES/GCM/NoPadding"
    }
}
