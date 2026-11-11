package tribixbite.cleverkeys.estoniantelemetry

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** No plaintext is ever written to disk. Injected only for JVM tests lacking AndroidKeyStore. */
internal interface CipherBox {
    fun seal(bytes: ByteArray): ByteArray
    fun open(bytes: ByteArray): ByteArray
    fun destroy()
}

internal class AndroidKeystoreBox(private val alias: String) : CipherBox {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    override fun seal(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return byteArrayOf(1, cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(bytes)
    }

    override fun open(bytes: ByteArray): ByteArray {
        require(bytes.size >= 30 && bytes[0] == 1.toByte())
        val length = bytes[1].toInt() and 255
        require(length == 12 && bytes.size >= 2 + length + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(2, 2 + length)))
        return cipher.doFinal(bytes.copyOfRange(2 + length, bytes.size))
    }

    override fun destroy() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}
