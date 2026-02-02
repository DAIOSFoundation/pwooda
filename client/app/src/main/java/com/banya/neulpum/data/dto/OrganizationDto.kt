package com.banya.neulpum.data.dto

import com.banya.neulpum.domain.entity.Organization

data class OrganizationDto(
    val id: String,
    val name: String,
    val description: String,
    val api_key: String,
    val is_active: Boolean
) {
    fun toEntity(): Organization {
        return Organization(
            id = id,
            name = name,
            description = description,
            apiKey = api_key,
            isActive = is_active
        )
    }
}

data class OrganizationResponse(
    val success: Boolean,
    val data: OrganizationDto? = null,
    val error: String? = null
)

data class JoinByApiKeyRequest(
    val api_key: String
)
