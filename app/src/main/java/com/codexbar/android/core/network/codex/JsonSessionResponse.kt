package com.codexbar.android.core.network.codex

import kotlinx.serialization.Serializable

@Serializable
data class JsonSessionResponse(
    val accessToken: String? = null,
    val user: JsonUser? = null,
    val plan: JsonPlan? = null,
    val expires: String? = null
)

@Serializable
data class JsonUser(
    val name: String? = null,
    val email: String? = null,
    val image: String? = null
)

@Serializable
data class JsonPlan(
    val id: String? = null,
    val title: String? = null,
    val interval: String? = null,
    val renewalDate: String? = null
)
