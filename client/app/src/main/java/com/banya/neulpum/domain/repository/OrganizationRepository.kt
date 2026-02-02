package com.banya.neulpum.domain.repository

import com.banya.neulpum.data.dto.OrganizationDto
import retrofit2.Response

interface OrganizationRepository {
    suspend fun lookupByApiKey(apiKey: String): Response<OrganizationDto>
    suspend fun joinByApiKey(apiKey: String): Response<OrganizationDto?>
    suspend fun regenerateApiKey(): Response<OrganizationDto?>
}