package com.banya.neulpum.data.datasource

import com.banya.neulpum.data.dto.OrganizationDto
import com.banya.neulpum.data.dto.OrganizationResponse
import com.banya.neulpum.data.dto.JoinByApiKeyRequest
import com.banya.neulpum.data.remote.OrganizationApiService
import retrofit2.Response
import okhttp3.ResponseBody

class OrganizationRemoteDataSource(
    private val apiService: OrganizationApiService
) {
    // getMyOrganization은 /my 엔드포인트에서 가져와야 하므로 이 메서드는 제거
    // 사용자의 현재 기관 정보는 AuthRepository에서 가져옴

    suspend fun lookupByApiKey(apiKey: String): Response<OrganizationDto> {
        val response = apiService.getOrganizationByApiKey(apiKey)
        return if (response.isSuccessful) {
            val responseData = response.body()
            println("DEBUG: API Response data: $responseData")
            if (responseData != null) {
                // daition_front와 동일하게 data 래퍼가 있을 수도 있고 없을 수도 있음
                val data = if (responseData.containsKey("data") && responseData["data"] != null) {
                    responseData["data"] as Map<String, Any>
                } else {
                    responseData
                }
                
                val orgDto = OrganizationDto(
                    id = data["id"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    api_key = data["api_key"] as? String ?: "",
                    is_active = data["is_active"] as? Boolean ?: true
                )
                println("DEBUG: Parsed OrganizationDto: $orgDto")
                Response.success(orgDto)
            } else {
                Response.error(404, ResponseBody.create(null, "No data"))
            }
        } else {
            println("DEBUG: API Error - Code: ${response.code()}, Message: ${response.message()}")
            Response.error(response.code(), ResponseBody.create(null, "API Error"))
        }
    }

    suspend fun joinByApiKey(apiKey: String, authToken: String? = null): Response<OrganizationDto?> {
        val request = mapOf("api_key" to apiKey)
        val response = apiService.joinOrganization(request, authToken, null)
        return if (response.isSuccessful) {
            val responseData = response.body()
            println("DEBUG: Join API Response data: $responseData")
            if (responseData != null) {
                // daition_front와 동일하게 data 래퍼가 있을 수도 있고 없을 수도 있음
                val data = if (responseData.containsKey("data") && responseData["data"] != null) {
                    responseData["data"] as Map<String, Any>
                } else {
                    responseData
                }
                
                val orgDto = OrganizationDto(
                    id = data["id"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    api_key = data["api_key"] as? String ?: "",
                    is_active = data["is_active"] as? Boolean ?: true
                )
                println("DEBUG: Parsed Join OrganizationDto: $orgDto")
                Response.success(orgDto)
            } else {
                Response.success(null)
            }
        } else {
            println("DEBUG: Join API Error - Code: ${response.code()}, Message: ${response.message()}")
            Response.error(response.code(), ResponseBody.create(null, "API Error"))
        }
    }

    suspend fun regenerateApiKey(organizationId: String): Response<OrganizationDto?> {
        val response = apiService.regenerateApiKey(organizationId)
        return if (response.isSuccessful) {
            val data = response.body()
            if (data != null) {
                val orgDto = OrganizationDto(
                    id = data["id"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    description = data["description"] as? String ?: "",
                    api_key = data["api_key"] as? String ?: "",
                    is_active = data["is_active"] as? Boolean ?: true
                )
                Response.success(orgDto)
            } else {
                Response.success(null)
            }
        } else {
            Response.error(response.code(), ResponseBody.create(null, "API Error"))
        }
    }
    
    suspend fun getMemberPrompts(memberId: String, authToken: String? = null): Response<Map<String, String>> {
        val response = apiService.getMemberPrompts(memberId, authToken)
        return if (response.isSuccessful) {
            val responseData = response.body()
            if (responseData != null) {
                val data = if (responseData.containsKey("data") && responseData["data"] != null) {
                    responseData["data"] as Map<String, Any>
                } else {
                    responseData
                }
                val prompts = mapOf(
                    "user_prompt" to (data["user_prompt"] as? String ?: ""),
                    "assistant_prompt" to (data["assistant_prompt"] as? String ?: "")
                )
                Response.success(prompts)
            } else {
                Response.success(mapOf("user_prompt" to "", "assistant_prompt" to ""))
            }
        } else {
            Response.error(response.code(), ResponseBody.create(null, "API Error"))
        }
    }
    
    suspend fun saveMemberPrompts(memberId: String, userPrompt: String, assistantPrompt: String, authToken: String? = null): Response<Map<String, Any>> {
        val request = mapOf(
            "user_prompt" to userPrompt,
            "assistant_prompt" to assistantPrompt
        )
        return apiService.saveMemberPrompts(memberId, request, authToken)
    }
}