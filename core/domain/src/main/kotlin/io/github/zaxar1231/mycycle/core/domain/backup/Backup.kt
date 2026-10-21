package io.github.zaxar1231.mycycle.core.domain.backup

enum class RestoreResult { RESTORED, WRONG_PASSWORD, NOT_A_BACKUP, FAILED }

/**
 * A copy of everything, encrypted with a password the user picks. The file stays wherever the
 * user saves it; the app never uploads it.
 */
interface BackupRepository {
    suspend fun exportEncrypted(password: CharArray): ByteArray

    /** Replaces all data with the backup's; nothing changes unless the whole file reads correctly. */
    suspend fun restoreEncrypted(file: ByteArray, password: CharArray): RestoreResult

    /** The day logs as a plain CSV table, unencrypted, for a spreadsheet. */
    suspend fun exportCsv(): String
}

object BackupRules {
    const val MIN_PASSWORD_LENGTH = 8

    fun isValidPassword(password: CharArray) = password.size >= MIN_PASSWORD_LENGTH
}
