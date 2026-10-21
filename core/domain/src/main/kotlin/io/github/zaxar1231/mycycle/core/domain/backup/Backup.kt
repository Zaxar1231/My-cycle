package io.github.zaxar1231.mycycle.core.domain.backup

enum class RestoreResult { RESTORED, WRONG_PASSWORD, NOT_A_BACKUP, FAILED }

/**
 * A copy of everything, encrypted with a password the user picks. The file stays wherever the
 * user saves it; the app never uploads it.
 */
interface BackupRepository {
    suspend fun exportEncrypted(password: CharArray): ByteArray

    /** Whether the first bytes of a file are those of a backup, before reading the rest. */
    fun looksLikeBackup(start: ByteArray): Boolean

    /** Replaces all data with the backup's; nothing changes unless the whole file reads correctly. */
    suspend fun restoreEncrypted(file: ByteArray, password: CharArray): RestoreResult

    /** The day logs as a plain CSV table, unencrypted, for a spreadsheet. Notes and sex only when asked for. */
    suspend fun exportCsv(includeNotes: Boolean, includeSex: Boolean): String
}

object BackupRules {
    const val MIN_PASSWORD_LENGTH = 8

    /** Larger files are not read: a real backup is far smaller, and reading one whole could run out of memory. */
    const val MAX_BACKUP_BYTES = 16L * 1024 * 1024

    fun isValidPassword(length: Int) = length >= MIN_PASSWORD_LENGTH
}
