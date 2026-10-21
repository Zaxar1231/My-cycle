package io.github.zaxar1231.mycycle.core.data.security

import io.github.zaxar1231.mycycle.core.data.settings.DataStoreSettingsRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccess
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccessStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EncryptedDataAccess @Inject constructor(
    private val keys: DatabaseKeyProvider,
    private val settings: DataStoreSettingsRepository,
    private val pin: KeystorePinRepository,
) : DataAccess {
    override suspend fun check(): DataAccessStatus = withContext(Dispatchers.IO) { keys.prepare() }

    override suspend fun resetUnreadableData() = withContext(Dispatchers.IO) {
        keys.reset()
        settings.resetAfterDataLoss()
        // A PIN for data that no longer exists would only lock the user out of a fresh start.
        pin.clear()
    }
}
