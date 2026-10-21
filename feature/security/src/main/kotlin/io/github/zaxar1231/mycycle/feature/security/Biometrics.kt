package io.github.zaxar1231.mycycle.feature.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi

/**
 * Fingerprint or face unlock through the system [BiometricPrompt]; available from Android 10.
 * The app never sees biometric data, only whether the system accepted it.
 */
internal object Biometrics {
    fun isAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Shows the prompt; [onSuccess] runs on the main thread. Cancel the returned signal to close it. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun authenticate(context: Context, title: String, cancel: String, onSuccess: () -> Unit, onEnd: () -> Unit): CancellationSignal {
        val executor = context.mainExecutor
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setNegativeButton(cancel, executor) { _, _ -> onEnd() }
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK) }
            .build()
        val signal = CancellationSignal()
        prompt.authenticate(
            signal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onEnd()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onEnd()
            },
        )
        return signal
    }
}
