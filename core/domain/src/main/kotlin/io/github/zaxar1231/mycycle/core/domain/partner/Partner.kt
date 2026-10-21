package io.github.zaxar1231.mycycle.core.domain.partner

import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate

/** What the person who tracks the cycle lets the partner see. Notes, sex and weight are never shared. */
enum class PartnerPermission { PHASE, MOOD, SYMPTOMS, FERTILITY }

/** Shared by default: only the phase and the period forecast; everything else is opt-in. */
val DEFAULT_PARTNER_PERMISSIONS: Set<PartnerPermission> = setOf(PartnerPermission.PHASE)

sealed interface PartnerState {
    /** The build has no server configured. */
    data object Unavailable : PartnerState

    /** Partner mode needs an account. */
    data object SignedOut : PartnerState

    /** No link. [endedByOther] is true right after the other side removed the link. */
    data class None(val endedByOther: Boolean = false) : PartnerState

    /** An invitation is waiting for the partner. [code] holds the encryption key: it is shown, never uploaded. */
    data class Inviting(val code: String, val expiresAt: Instant) : PartnerState

    /**
     * Someone accepted the invitation. Nothing is shared until the owner checks that [verification]
     * matches the code on the partner's phone, so a leaked invitation cannot be used unnoticed.
     */
    data class Confirming(val verification: String) : PartnerState

    /** This phone tracks the cycle and shares it with a partner. */
    data class Sharing(val permissions: Set<PartnerPermission>) : PartnerState

    /** This phone follows the cycle of the person who invited it; [verification] is shown to compare. */
    data class Following(val notify: Boolean, val verification: String) : PartnerState

    /**
     * The server has a link for this account but this phone has no key for it (reinstall, another
     * phone): nothing can be read or shared, the link can only be removed.
     */
    data object Orphaned : PartnerState
}

enum class PartnerError {
    INVALID_CODE,
    /** The invitation was used, cancelled or has expired. */
    INVITE_NOT_FOUND,
    OWN_INVITE,
    ALREADY_LINKED,
    /** The server has no partner tables yet: the SQL from docs/supabase was not run. */
    SERVER_NOT_READY,
    SIGNED_OUT,
    NETWORK,
    UNKNOWN,
}

sealed interface PartnerResult {
    data object Done : PartnerResult
    data class Failed(val error: PartnerError) : PartnerResult
}

/** What the partner gets: the latest shared snapshot, if the owner has sent one. */
sealed interface SharedCycle {
    data class Shared(val snapshot: PartnerSnapshot) : SharedCycle

    /** Linked, but nothing was shared yet. */
    data object Waiting : SharedCycle

    /** The link is gone; the shared data was removed with it. */
    data object Ended : SharedCycle
    data class Failed(val error: PartnerError) : SharedCycle
}

/**
 * Partner mode with end-to-end encryption. The server only stores ciphertext; the key travels
 * from phone to phone inside the invitation code. Behind an interface so the backend can change.
 */
interface PartnerRepository {
    val state: StateFlow<PartnerState>

    /** Checks the link on the server: accepted invitations, links removed by the other side. */
    suspend fun refresh(): PartnerResult
    suspend fun createInvite(): PartnerResult
    suspend fun cancelInvite()
    suspend fun acceptInvite(code: String): PartnerResult

    /** Owner: the verification codes match, sharing starts. */
    suspend fun confirmPartner()
    suspend fun setPermissions(permissions: Set<PartnerPermission>)
    suspend fun setNotify(enabled: Boolean)

    /** Removes the link on the server; the shared data goes with it. */
    suspend fun unlink(): PartnerResult

    /** Owner: encrypts and uploads [snapshot]. */
    suspend fun share(snapshot: PartnerSnapshot): PartnerResult

    /** Partner: downloads and decrypts the shared snapshot. */
    suspend fun fetchShared(): SharedCycle

    /** Partner: the period start the last reminder was about, so each period is announced once. */
    suspend fun lastNotice(): LocalDate?
    suspend fun setLastNotice(date: LocalDate)
}
