package io.github.zaxar1231.mycycle.feature.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Fingerprint or face unlock through the system [BiometricPrompt], Android 10 and later, strong
 * (class 3) biometrics only. Success is proven with a Keystore key that only a biometric match
 * unlocks and that dies when a new fingerprint or face is enrolled, so someone who adds their own
 * fingerprint to the phone cannot open the app with it. The app never sees biometric data.
 */
internal object Biometrics {
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Creates the key when biometric unlock is turned on. Returns false if the device refuses. */
    fun enable(): Boolean = runCatching { createKey() }.isSuccess

    fun disable() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /** False when the key is missing or was invalidated by a new enrollment; then the PIN is needed. */
    fun isEnrolled(): Boolean = cipher() != null

    /** Shows the prompt; [onSuccess] runs on the main thread. Cancel the returned signal to close it. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun authenticate(context: Context, title: String, cancel: String, onSuccess: () -> Unit, onEnd: () -> Unit): CancellationSignal? {
        val cipher = cipher() ?: return null
        val executor = context.mainExecutor
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setNegativeButton(cancel, executor) { _, _ -> onEnd() }
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG) }
            .build()
        val signal = CancellationSignal()
        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            signal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onEnd()
                    // Only a real biometric match makes the key usable; a forged callback fails here.
                    val proven = runCatching { result.cryptoObject?.cipher?.doFinal(PROOF) }.getOrNull() != null
                    if (proven) onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onEnd()
            },
        )
        return signal
    }

    private fun cipher(): Cipher? = runCatching {
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        // Throws KeyPermanentlyInvalidatedException after a new enrollment.
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }.getOrNull()

    private fun createKey() {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "twilight_biometric_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val PROOF = byteArrayOf(1)
}
