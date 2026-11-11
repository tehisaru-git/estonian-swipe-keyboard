package tribixbite.cleverkeys.estoniantelemetry

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** JVM tests use JCE because Robolectric cannot emulate the hardware-backed AndroidKeyStore. */
internal class TestCipherBox : CipherBox {
    private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    override fun seal(bytes: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key)
        byteArrayOf(1, iv.size.toByte()) + iv + doFinal(bytes)
    }
    override fun open(bytes: ByteArray): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        val length = bytes[1].toInt() and 255
        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(2, 2 + length)))
        doFinal(bytes.copyOfRange(2 + length, bytes.size))
    }
    override fun destroy() = Unit
}
