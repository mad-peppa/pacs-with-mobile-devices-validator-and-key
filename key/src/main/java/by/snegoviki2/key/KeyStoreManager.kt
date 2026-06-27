package by.snegoviki2.key

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.crypto.KeyGenerator

class KeyStoreManager(val alias: String = "user_key") {
    companion object {
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_RSA
        private const val KEY_SIZE = 2048
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val PADDING = KeyProperties.SIGNATURE_PADDING_RSA_PKCS1
    }
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    fun isKeyExists(): Boolean {
        return keyStore.containsAlias(alias)
    }

    fun generateKeyPair(){
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KEY_ALGORITHM,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(KEY_SIZE)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(PADDING)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }
    fun getPublicKey(): PublicKey? {
        return keyStore.getCertificate(alias)?.publicKey
    }
    fun getPublicKeyInString():String?{
        val key:PublicKey? = getPublicKey()
        if (key!=null){
            val keyBytes = key.encoded
            return android.util.Base64.encodeToString(keyBytes, android.util.Base64.DEFAULT)
        } else
            return null
    }
    fun getPublicKeyInPEM(): String {
        val base64 = getPublicKeyInString() ?: return ""
        return "-----BEGIN PUBLIC KEY-----$base64-----END PUBLIC KEY-----"
    }
    fun deleteKey() {
        if (isKeyExists()) {
            keyStore.deleteEntry(alias)
        }
    }
    fun signData(data: String): String? {
        return try {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                ?: return null

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(entry.privateKey)
            signature.update(data.toByteArray())

            val signedBytes = signature.sign()
            android.util.Base64.encodeToString(signedBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}