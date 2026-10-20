package io.github.zaxar1231.mycycle.core.domain.model

/** Link between the account that tracks the cycle and a partner account (stage 9). */
data class PartnerLink(
    val ownerId: String,
    val partnerId: String,
    val permissions: Set<PartnerPermission>,
    val status: PartnerLinkStatus,
)

enum class PartnerPermission { PHASE, MOOD, SYMPTOMS, FERTILITY }

enum class PartnerLinkStatus { PENDING, ACTIVE, REVOKED }
