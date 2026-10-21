package io.github.zaxar1231.mycycle.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.repository.PinRepository
import io.github.zaxar1231.mycycle.core.domain.security.PinCheck
import io.github.zaxar1231.mycycle.core.domain.security.PinPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores HMAC-SHA256(salt + PIN) with a key that lives only in the Android Keystore, in
 * no-backup storage. A four-digit PIN is easy to guess offline from a plain hash; with a
 * Keystore key every guess has to go through this device, where wrong attempts freeze input.
 * The failure counter lives in DataStore, so restarting the app does not reset it.
 */
@Singleton
class KeystorePinRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val store: DataStore<Preferences>,
) : PinRepository {
    private val file = AtomicFile(File(context.noBackupFilesDir, PIN_FILE))
    private val lock = Mutex()
    private val present = MutableStateFlow(file.baseFile.exists())

    override val hasPin: Flow<Boolean> = present.asStateFlow()

    override suspend fun setPin(pin: String) = lock.withLock {
        require(PinPolicy.isValid(pin)) { "PIN must be ${PinPolicy.LENGTH} digits" }
        withContext(Dispatchers.IO) {
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val stored = byteArrayOf(FORMAT_VERSION) + salt + mac(createKey(), salt, pin)
            val out = file.startWrite()
            try {
                out.write(stored)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
        resetFailures()
        present.value = true
    }

    override suspend fun check(pin: String): PinCheck = lock.withLock {
        val now = System.currentTimeMillis()
        frozenUntil(now)?.let { return PinCheck.LockedOut(it) }
        if (matches(pin)) {
            resetFailures()
            return PinCheck.Correct
        }
        var failures = 0
        var until: Long? = null
        store.edit { prefs ->
            failures = (prefs[FAILURES] ?: 0) + 1
            prefs[FAILURES] = failures
            val freeze = PinPolicy.lockoutAfter(failures)
            if (freeze > 0) {
                until = now + freeze
                prefs[LOCKED_UNTIL] = now + freeze
            }
        }
        PinCheck.Wrong(attemptsLeft = (PinPolicy.FREE_ATTEMPTS - failures).coerceAtLeast(0), lockedUntilMillis = until)
    }

    override suspend fun lockedUntil(): Long? = lock.withLock { frozenUntil(System.currentTimeMillis()) }

    override suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) {
            file.delete()
            runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        }
        resetFailures()
        present.value = false
    }

    /** A freeze longer than the maximum means the clock was moved back; it is shortened to the maximum. */
    private suspend fun frozenUntil(now: Long): Long? {
        val until = store.data.first()[LOCKED_UNTIL] ?: return null
        return when {
            until <= now -> null
            until - now > PinPolicy.MAX_LOCKOUT_MILLIS -> now + PinPolicy.MAX_LOCKOUT_MILLIS
            else -> until
        }
    }

    private suspend fun matches(pin: String): Boolean = withContext(Dispatchers.IO) {
        if (!PinPolicy.isValid(pin) || !file.baseFile.exists()) return@withContext false
        runCatching {
            val stored = file.readFully()
            if (stored.size != 1 + SALT_BYTES + MAC_BYTES || stored[0] != FORMAT_VERSION) return@runCatching false
            val salt = stored.copyOfRange(1, 1 + SALT_BYTES)
            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return@runCatching false
            MessageDigest.isEqual(mac(key, salt, pin), stored.copyOfRange(1 + SALT_BYTES, stored.size))
        }.getOrDefault(false)
    }

    private suspend fun resetFailures() {
        store.edit { prefs ->
            prefs.remove(FAILURES)
            prefs.remove(LOCKED_UNTIL)
        }
    }

    private fun mac(key: SecretKey, salt: ByteArray, pin: String): ByteArray =
        Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).run {
            init(key)
            update(salt)
            doFinal(pin.toByteArray(Charsets.US_ASCII))
        }

    private fun createKey(): SecretKey {
        keyStore().deleteEntry(KEY_ALIAS)
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "twilight_pin_key"
        const val PIN_FILE = "pin.hash"
        const val FORMAT_VERSION: Byte = 1
        const val SALT_BYTES = 16
        const val MAC_BYTES = 32
        val FAILURES = intPreferencesKey("pin_failures")
        val LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
    }
}
