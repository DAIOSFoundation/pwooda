package com.banya.neulpum.data.repository

import android.content.Context
import com.banya.neulpum.data.dto.OrganizationDto
import com.banya.neulpum.data.datasource.OrganizationRemoteDataSource
import com.banya.neulpum.data.remote.OrganizationApiService
import com.banya.neulpum.domain.repository.OrganizationRepository
import com.banya.neulpum.domain.repository.AuthRepository
import com.banya.neulpum.di.NetworkModule
import retrofit2.Response

class OrganizationRepositoryImpl(context: Context) : OrganizationRepository {
    private val apiService: OrganizationApiService = NetworkModule.provideOrganizationApiService()
    private val dataSource = OrganizationRemoteDataSource(apiService)
    private val authRepository = AuthRepositoryImpl(context)


    override suspend fun lookupByApiKey(apiKey: String): Response<OrganizationDto> {
        return dataSource.lookupByApiKey(apiKey)
    }

    override suspend fun joinByApiKey(apiKey: String): Response<OrganizationDto?> {
        val token = authRepository.getAccessToken()
        return dataSource.joinByApiKey(apiKey, "Bearer $token")
    }

    override suspend fun regenerateApiKey(): Response<OrganizationDto?> {
        // TODO: organizationId를 어떻게 가져올지 결정 필요
        return dataSource.regenerateApiKey("")
    }
}