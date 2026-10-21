package io.github.zaxar1231.mycycle.core.sync.partner

import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads PostgREST answers: error bodies to [PartnerError] and rows to plain values. */
internal object PartnerErrors {
    private const val UNAUTHORIZED = 401

    fun map(httpCode: Int, body: String): PartnerError {
        val text = body.lowercase()
        return when {
            // A table or function that does not exist: the SQL from docs/supabase was not run.
            "pgrst205" in text || "pgrst202" in text || "42p01" in text || "42883" in text -> PartnerError.SERVER_NOT_READY
            "invite_not_found" in text -> PartnerError.INVITE_NOT_FOUND
            "own_invite" in text -> PartnerError.OWN_INVITE
            "already_linked" in text || "23505" in text -> PartnerError.ALREADY_LINKED
            "not_signed_in" in text || httpCode == UNAUTHORIZED -> PartnerError.SIGNED_OUT
            else -> PartnerError.UNKNOWN
        }
    }

    /** The values of [field] in a JSON array of rows; empty when the body is not one. */
    fun column(body: String, field: String): List<String> = runCatching {
        Json.parseToJsonElement(body).jsonArray.mapNotNull { row -> row.jsonObject[field]?.jsonPrimitive?.content }
    }.getOrDefault(emptyList())
}
