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
import io.github.zaxar1231.mycycle.core.domain.partner.SharedCycle
import io.github.zaxar1231.mycycle.core.sync.SupabaseHttp
import io.github.zaxar1231.mycycle.core.sync.partner.StoredLink.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val stored = MutableStateFlow(store.read())
    private val orphaned = MutableStateFlow(false)
    private val ended = MutableStateFlow(false)

    override val state: StateFlow<PartnerState> = combine(accounts.account, stored, orphaned, ended) { a, l, o, e -> stateOf(a, l, o, e) }
        .stateIn(scope, SharingStarted.Eagerly, stateOf(accounts.account.value, stored.value, orphaned.value, ended.value))

    private fun stateOf(account: Account?, link: StoredLink?, orphan: Boolean, endedByOther: Boolean): PartnerState {
        if (!http.isConfigured) return PartnerState.Unavailable
        if (account == null) return PartnerState.SignedOut
        val mine = link?.takeIf { it.userId == account.userId }
        return when (mine?.role) {
            Role.INVITING -> PartnerState.Inviting(InviteCode.format(mine.invite()), Instant.ofEpochMilli(mine.expiresAt))
            Role.SHARING -> PartnerState.Sharing(mine.permissionSet())
            Role.FOLLOWING -> PartnerState.Following(mine.notify)
            null -> if (orphan) PartnerState.Orphaned else PartnerState.None(endedByOther)
        }
    }

    override suspend fun refresh(): PartnerResult = call {
        val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
        val response = http.request("GET", "/rest/v1/partner_links?select=id", bearer = bearer)
        if (!response.ok) return@call failed(response)
        val links = PartnerErrors.column(response.body, "id").toSet()
        lock.withLock {
            val link = current()
            when {
                link == null -> orphaned.value = links.isNotEmpty()
                link.role == Role.INVITING -> when {
                    link.linkId in links -> save(link.copy(role = Role.SHARING, permissions = DEFAULT_PARTNER_PERMISSIONS.names()))
                    // An old link of this account blocks the invitation; it has to be removed first.
                    links.isNotEmpty() -> {
                        save(null)
                        orphaned.value = true
                    }
                    System.currentTimeMillis() > link.expiresAt -> save(null)
                }
                link.linkId !in links -> {
                    save(null)
                    ended.value = true
                }
            }
            Unit
        }
        PartnerResult.Done
    }

    override suspend fun createInvite(): PartnerResult = call {
        lock.withLock {
            val account = accounts.account.value ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            if (current()?.role.let { it == Role.SHARING || it == Role.FOLLOWING }) {
                return@call PartnerResult.Failed(PartnerError.ALREADY_LINKED)
            }
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
            orphaned.value = false
            ended.value = false
            PartnerResult.Done
        }
    }

    override suspend fun cancelInvite() {
        lock.withLock {
            val link = current()?.takeIf { it.role == Role.INVITING } ?: return
            save(null)
            // Best effort: the code is useless without this phone anyway, and the server drops it after a day.
            runCatching {
                val bearer = accounts.accessToken() ?: return
                http.request("DELETE", "/rest/v1/partner_invites?id=eq.${link.linkId}", bearer = bearer)
            }
        }
    }

    override suspend fun acceptInvite(code: String): PartnerResult = call {
        val invite = InviteCode.parse(code) ?: return@call PartnerResult.Failed(PartnerError.INVALID_CODE)
        lock.withLock {
            val account = accounts.account.value ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            if (current()?.role.let { it == Role.SHARING || it == Role.FOLLOWING }) {
                return@call PartnerResult.Failed(PartnerError.ALREADY_LINKED)
            }
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
            orphaned.value = false
            ended.value = false
            PartnerResult.Done
        }
    }

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
            orphaned.value = false
            ended.value = false
            PartnerResult.Done
        }
    }

    override suspend fun share(snapshot: PartnerSnapshot): PartnerResult {
        val result = call {
            val link = lock.withLock { current() }?.takeIf { it.role == Role.SHARING } ?: return@call PartnerResult.Done
            val bearer = accounts.accessToken() ?: return@call PartnerResult.Failed(PartnerError.SIGNED_OUT)
            val payload = PartnerCrypto.seal(link.keyBytes(), link.linkId, PartnerCodec.encode(snapshot))
            val body = buildJsonObject {
                put("link_id", link.linkId)
                put("payload", payload)
                put("updated_at", Instant.now().toString())
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

    override suspend fun lastNotice(): LocalDate? =
        lock.withLock { current() }?.lastNotice?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setLastNotice(date: LocalDate) = update(Role.FOLLOWING) { it.copy(lastNotice = date.toString()) }

    /** The stored link of the signed-in account. */
    private fun current(): StoredLink? {
        val account = accounts.account.value ?: return null
        return stored.value?.takeIf { it.userId == account.userId }
    }

    private fun save(link: StoredLink?) {
        if (link == null) store.clear() else store.write(link)
        stored.value = link
    }

    private suspend fun update(role: Role, change: (StoredLink) -> StoredLink) {
        lock.withLock {
            val link = current()?.takeIf { it.role == role } ?: return
            runCatching { save(change(link)) }
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

    private fun StoredLink.keyBytes(): ByteArray = Base64.getDecoder().decode(key)

    private fun StoredLink.invite() = Invite(UUID.fromString(linkId), keyBytes())

    private fun StoredLink.permissionSet(): Set<PartnerPermission> =
        permissions.mapNotNull { name -> PartnerPermission.entries.firstOrNull { it.name == name } }.toSet()

    private fun Set<PartnerPermission>.names() = map { it.name }.sorted()

    private companion object {
        const val PREFER = "Prefer"
        const val RETURN_MINIMAL = "return=minimal"

        /** A little under the server's day, so the phone never shows a code the server already dropped. */
        const val INVITE_LIFETIME_MILLIS = 23 * 60 * 60 * 1000L
    }
}
