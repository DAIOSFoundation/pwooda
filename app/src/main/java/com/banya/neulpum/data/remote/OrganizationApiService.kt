package com.banya.neulpum.data.remote

import com.banya.neulpum.domain.entity.Organization
import com.banya.neulpum.domain.entity.OrganizationCreate
import retrofit2.Response
import retrofit2.http.*

interface OrganizationApiService {
    
    @GET("organizations/by-api-key")
    suspend fun getOrganizationByApiKey(
        @Query("api_key") apiKey: String,
        @Header("X-API-Key") headerApiKey: String? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<Map<String, Any>>
    
    @POST("organizations/")
    suspend fun createOrganization(
        @Body organization: OrganizationCreate,
        @Header("X-API-Key") apiKey: String? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<Map<String, Any>>
    
    @POST("organizations/join-by-api-key")
    suspend fun joinOrganization(
        @Body request: Map<String, String>,
        @Header("Authorization") authorization: String? = null,
        @Header("X-API-Key") apiKey: String? = null
    ): Response<Map<String, Any>>
    
    @POST("organizations/{organizationId}/regenerate-api-key")
    suspend fun regenerateApiKey(
        @Path("organizationId") organizationId: String,
        @Header("X-API-Key") apiKey: String? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<Map<String, Any>>
}