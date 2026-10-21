package io.github.zaxar1231.mycycle.core.sync.partner

import io.github.zaxar1231.mycycle.core.domain.account.Account
import io.github.zaxar1231.mycycle.core.domain.account.AccountRepository
import io.github.zaxar1231.mycycle.core.domain.partner.DEFAULT_PARTNER_PERMISSIONS
import io.github.zaxar1231.mycycle.core.domain.partner.Invite
import io.github.zaxar1231.mycycle.core.domain.partner.InviteCode
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerPermission
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerRepository
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerResult
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerSnapshot
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerState
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerVerification
import io.github.zaxar1231.mycycle.core.domain.pet.PetCare
import io.github.zaxar1231.mycycle.core.domain.partner.SharedCycle
import io.github.zaxar1231.mycycle.core.sync.SupabaseHttp
import io.github.zaxar1231.mycycle.core.sync.partner.StoredLink.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Partner mode on Supabase (tables and rules: docs/supabase/001_partner.sql). Everything shared is
 * encrypted here with the key from the invitation before it is sent; the server only ever gets
 * the link id, the two account ids and ciphertext.
 */
@Singleton
class SupabasePartnerRepository @Inject constructor(
    private val http: SupabaseHttp,
    private val accounts: AccountRepository,
    private val store: PartnerStore,
) : PartnerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    // Changed only under [lock]; every change publishes [state] at once, so a read right after a
    // call already sees its result. [generation] counts changes, so a slow server answer that
    // started before one is not applied on top of it.
    @Volatile private var stored: Map<String, StoredLink> = store.read()
        set(value) {
            field = value
            changed()
        }
    private var orphaned = false
        set(value) {
            field = value
            changed()
        }
    private var ended = false
        set(value) {
            field = value
            changed()
        }
    private var generation = 0L

    private val _state = MutableStateFlow(stateOf(accounts.account.value, stored, orphaned, ended))
    override val state: StateFlow<PartnerState> = _state.asStateFlow()

    init {
        scope.launch { accounts.account.collect { publish() } }
    }

    private fun changed() {
        generation++
        publish()
    }

    private fun publish() {
        _state.value = stateOf(accounts.account.value, stored, orphaned, ended)
    }

    private fun stateOf(account: Account?, links: Map<String, StoredLink>, orphan: Boolean, endedByOther: Boolean): PartnerState {
        if (!http.isConfigured) return PartnerState.Unavailable
        if (account == null) return PartnerState.SignedOut
        val mine = links[account.userId]
        return when (mine?.role) {
            Role.INVITING -> PartnerState.Inviting(InviteCode.format(mine.invite()), Instant.ofEpochMilli(mine.expiresAt))
            Role.CONFIRMING -> PartnerState.Confirming(PartnerVerification.code(mine.keyBytes(), mine.partnerId.orEmpty()))
            Role.SHARING -> PartnerState.Sharing(mine.permissionSet())
            Role.FOLLOWING -> PartnerState.Following(
                notify = mine.notify,
                verification = PartnerVerification.code(mine.keyBytes(), account.userId),
                lastFed = mine.lastFed.toDate(),
                lastGift = mine.lastGift.toDate(),
            )
            null -> if (orphan) PartnerState.Orphaned else PartnerState.None(endedByOther)
        }
    }

    override suspend fun refresh(): PartnerResult = call {
        val before = lock.withLock { generation }
        val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
        val response = http.request("GET", "/rest/v1/partner_links?select=id,partner", bearer = bearer)
        if (!response.ok) return@call failed(response)
        val partnersById = PartnerErrors.column(response.body, "id").zip(PartnerErrors.column(response.body, "partner")).toMap()
        val links = partnersById.keys
        lock.withLock {
            // Something changed while the request was out (joined, unlinked): this answer is stale.
            if (generation != before) return@withLock Unit
            val link = current()
            when {
                link == null -> orphaned = links.isNotEmpty()
                link.role == Role.INVITING -> when {
                    // Accepted: nothing is shared until the owner compares the verification codes.
                    link.linkId in links -> save(link.copy(role = Role.CONFIRMING, partnerId = partnersById[link.linkId]))
                    // An old link of this account blocks the invitation; it has to be removed first.
                    links.isNotEmpty() -> {
                        save(null)
                        orphaned = true
                    }
                    System.currentTimeMillis() > link.expiresAt -> save(null)
                }
                link.linkId !in links -> {
                    save(null)
                    ended = true
                }
            }
            Unit
        }
        PartnerResult.Done
    }

    override suspend fun createInvite(): PartnerResult = call {
        lock.withLock {
            val account = accounts.account.value ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            if (current()?.isLinked() == true) return@call PartnerResult.Failed(PartnerError.ALREADY_LINKED)
            val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            // A new invitation replaces the old ones, so an earlier code stops working.
            val cleared = http.request("DELETE", "/rest/v1/partner_invites?owner=eq.${account.userId}", bearer = bearer)
            if (!cleared.ok) return@call failed(cleared)
            val invite = Invite(UUID.randomUUID(), PartnerCrypto.newKey())
            val body = buildJsonObject { put("id", invite.linkId.toString()) }.toString()
            val created = http.request("POST", "/rest/v1/partner_invites", body, bearer, mapOf(PREFER to RETURN_MINIMAL))
            if (!created.ok) return@call failed(created)
            save(
                StoredLink(
                    userId = account.userId,
                    role = Role.INVITING,
                    linkId = invite.linkId.toString(),
                    key = Base64.getEncoder().encodeToString(invite.key),
                    expiresAt = System.currentTimeMillis() + INVITE_LIFETIME_MILLIS,
                ),
            )
            orphaned = false
            ended = false
            PartnerResult.Done
        }
    }

    override suspend fun cancelInvite() {
        lock.withLock {
            val link = current()?.takeIf { it.role == Role.INVITING } ?: return
            save(null)
            // Best effort: the server refuses an invitation older than a day and clears such rows on the next accept.
            try {
                val bearer = accounts.accessToken() ?: return
                http.request("DELETE", "/rest/v1/partner_invites?id=eq.${link.linkId}", bearer = bearer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline: the invitation runs out on its own.
            }
        }
    }

    override suspend fun acceptInvite(code: String): PartnerResult = call {
        val invite = InviteCode.parse(code) ?: return@call PartnerResult.Failed(PartnerError.INVALID_CODE)
        lock.withLock {
            val account = accounts.account.value ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            if (current()?.isLinked() == true) return@call PartnerResult.Failed(PartnerError.ALREADY_LINKED)
            val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            val body = buildJsonObject { put("invite_id", invite.linkId.toString()) }.toString()
            val response = http.request("POST", "/rest/v1/rpc/accept_partner_invite", body, bearer)
            if (!response.ok) return@call failed(response)
            save(
                StoredLink(
                    userId = account.userId,
                    role = Role.FOLLOWING,
                    linkId = invite.linkId.toString(),
                    key = Base64.getEncoder().encodeToString(invite.key),
                ),
            )
            orphaned = false
            ended = false
            PartnerResult.Done
        }
    }

    override suspend fun confirmPartner() =
        update(Role.CONFIRMING) { it.copy(role = Role.SHARING, permissions = DEFAULT_PARTNER_PERMISSIONS.names()) }

    override suspend fun setPermissions(permissions: Set<PartnerPermission>) = update(Role.SHARING) { it.copy(permissions = permissions.names()) }

    override suspend fun setNotify(enabled: Boolean) = update(Role.FOLLOWING) { it.copy(notify = enabled) }

    override suspend fun unlink(): PartnerResult = call {
        lock.withLock {
            val account = accounts.account.value ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            // Every link of this account, so an orphaned one (no key on this phone) goes too.
            // The shared snapshot is removed with the link by the database.
            val filter = "or=(owner.eq.${account.userId},partner.eq.${account.userId})"
            val response = http.request("DELETE", "/rest/v1/partner_links?$filter", bearer = bearer)
            if (!response.ok) return@call failed(response)
            if (current() != null) save(null)
            orphaned = false
            ended = false
            PartnerResult.Done
        }
    }

    override suspend fun share(snapshot: PartnerSnapshot): PartnerResult {
        val result = call {
            val link = lock.withLock { current() }?.takeIf { it.role == Role.SHARING } ?: return@call PartnerResult.Done
            val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            val payload = PartnerCrypto.seal(link.keyBytes(), link.linkId, PartnerCodec.encode(snapshot))
            // No timestamp: the snapshot carries its date inside the ciphertext.
            val body = buildJsonObject {
                put("link_id", link.linkId)
                put("payload", payload)
            }.toString()
            val headers = mapOf(PREFER to "resolution=merge-duplicates,$RETURN_MINIMAL")
            val response = http.request("POST", "/rest/v1/partner_shares?on_conflict=link_id", body, bearer, headers)
            if (response.ok) PartnerResult.Done else failed(response)
        }
        // A refused write usually means the partner removed the link: find out, so the screen says so.
        if (result is PartnerResult.Failed && result.error != PartnerError.NETWORK) refresh()
        return result
    }

    override suspend fun fetchShared(): SharedCycle {
        val link = lock.withLock { current() }?.takeIf { it.role == Role.FOLLOWING } ?: return SharedCycle.Ended
        return try {
            val bearer = accounts.accessToken() ?: return SharedCycle.Failed(PartnerError.SIGNED_OUT)
            val response = http.request("GET", "/rest/v1/partner_shares?select=payload&link_id=eq.${link.linkId}", bearer = bearer)
            if (!response.ok) return SharedCycle.Failed(PartnerErrors.map(response.code, response.body))
            val payload = PartnerErrors.column(response.body, "payload").firstOrNull()
            if (payload == null) {
                // No snapshot: either nothing was shared yet or the link is gone with its data.
                refresh()
                val stillLinked = lock.withLock { current() }?.role == Role.FOLLOWING
                return if (stillLinked) SharedCycle.Waiting else SharedCycle.Ended
            }
            val snapshot = PartnerCrypto.open(link.keyBytes(), link.linkId, payload)?.let(PartnerCodec::decode)
            if (snapshot != null) SharedCycle.Shared(snapshot) else SharedCycle.Failed(PartnerError.UNKNOWN)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SharedCycle.Failed(PartnerError.NETWORK)
        } catch (e: Exception) {
            SharedCycle.Failed(PartnerError.UNKNOWN)
        }
    }

    override suspend fun sendCare(care: PetCare): PartnerResult = call {
        val link = lock.withLock { current() }?.takeIf { it.role == Role.FOLLOWING } ?: return@call PartnerResult.Failed(PartnerError.UNKNOWN)
        val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
        val payload = PartnerCrypto.seal(link.keyBytes(), careAad(link.linkId), PartnerCodec.encodeCare(care))
        val body = buildJsonObject {
            put("link_id", link.linkId)
            put("payload", payload)
        }.toString()
        val response = http.request("POST", "/rest/v1/partner_pet_care", body, bearer, mapOf(PREFER to RETURN_MINIMAL))
        if (!response.ok) return@call failed(response)
        when (care) {
            is PetCare.Feed -> update(Role.FOLLOWING) { it.copy(lastFed = care.date.toString()) }
            is PetCare.Gift -> update(Role.FOLLOWING) { it.copy(lastGift = care.date.toString()) }
        }
        PartnerResult.Done
    }

    override suspend fun takeCare(apply: suspend (List<PetCare>) -> Boolean) {
        try {
            val link = lock.withLock { current() }?.takeIf { it.role == Role.SHARING }
            val bearer = if (link != null) accounts.accessToken() else null
            if (link != null && bearer != null) {
                val response = http.request("GET", "/rest/v1/partner_pet_care?select=id,payload&link_id=eq.${link.linkId}&limit=$CARE_BATCH", bearer = bearer)
                val ids = if (response.ok) PartnerErrors.column(response.body, "id") else emptyList()
                val payloads = if (response.ok) PartnerErrors.column(response.body, "payload") else emptyList()
                val cares = payloads.mapNotNull { payload ->
                    PartnerCrypto.open(link.keyBytes(), careAad(link.linkId), payload)?.let(PartnerCodec::decodeCare)
                }
                // Removed only once applied; applying twice is harmless, the limits catch repeats.
                if (ids.isNotEmpty() && apply(cares)) {
                    http.request("DELETE", "/rest/v1/partner_pet_care?id=in.(${ids.joinToString(",")})", bearer = bearer)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or a damaged row: the care stays queued for the next try.
        }
    }

    override suspend fun lastNotice(): LocalDate? = lock.withLock { current() }?.lastNotice.toDate()

    override suspend fun setLastNotice(date: LocalDate) = update(Role.FOLLOWING) { it.copy(lastNotice = date.toString()) }

    /** The stored link of the signed-in account. */
    private fun current(): StoredLink? {
        val account = accounts.account.value ?: return null
        return stored[account.userId]
    }

    /** Replaces (or with null removes) the signed-in account's link; other accounts' links stay. */
    private suspend fun save(link: StoredLink?) {
        val account = accounts.account.value ?: return
        val links = if (link == null) stored - account.userId else stored + (account.userId to link)
        // Keystore and file work stays off the main thread.
        withContext(Dispatchers.IO) { store.write(links) }
        stored = links
    }

    private fun StoredLink.isLinked() = role == Role.CONFIRMING || role == Role.SHARING || role == Role.FOLLOWING

    private suspend fun update(role: Role, change: (StoredLink) -> StoredLink) {
        lock.withLock {
            val link = current()?.takeIf { it.role == role } ?: return
            try {
                save(change(link))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A Keystore failure: the setting stays as it was.
            }
        }
    }

    private fun failed(response: SupabaseHttp.Response) = PartnerResult.Failed(PartnerErrors.map(response.code, response.body))

    private suspend fun call(block: suspend () -> PartnerResult): PartnerResult {
        if (!http.isConfigured) return PartnerResult.Failed(PartnerError.UNKNOWN)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            PartnerResult.Failed(PartnerError.NETWORK)
        } catch (e: Exception) {
            // An unexpected answer or a Keystore failure: reported, never a crash.
            PartnerResult.Failed(PartnerError.UNKNOWN)
        }
    }

    private fun String?.toDate(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Care messages are bound to their own context, so a snapshot can never pass for one. */
    private fun careAad(linkId: String) = "$linkId/care"

    private fun StoredLink.keyBytes(): ByteArray = Base64.getDecoder().decode(key)

    private fun StoredLink.invite() = Invite(UUID.fromString(linkId), keyBytes())

    private fun StoredLink.permissionSet(): Set<PartnerPermission> =
        permissions.mapNotNull { name -> PartnerPermission.entries.firstOrNull { it.name == name } }.toSet()

    private fun Set<PartnerPermission>.names() = map { it.name }.sorted()

    private companion object {
        const val PREFER = "Prefer"
        const val RETURN_MINIMAL = "return=minimal"

        /** Care rows read at a time; the server keeps at most a few per link anyway. */
        const val CARE_BATCH = 50

        /** A little under the server's day, so the phone never shows a code the server already dropped. */
        const val INVITE_LIFETIME_MILLIS = 23 * 60 * 60 * 1000L
    }
}
