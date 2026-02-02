package com.banya.neulpum.domain.entity

data class User(
    val id: String,
    val email: String,
    val name: String,
    val isLoggedIn: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val profileUrl: String? = null,
    val createdAt: String? = null,
    val organizationName: String? = null,
    val organizationApiKey: String? = null,
    val organizationDescription: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class SignupRequest(
    val email: String,
    val password: String,
    val name: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val user: User? = null,
    val accessToken: String? = null
)
