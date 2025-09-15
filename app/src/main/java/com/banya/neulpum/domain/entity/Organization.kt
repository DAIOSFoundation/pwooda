package com.banya.neulpum.domain.entity

data class Organization(
    val id: String,
    val name: String,
    val description: String,
    val apiKey: String,
    val isActive: Boolean
)
