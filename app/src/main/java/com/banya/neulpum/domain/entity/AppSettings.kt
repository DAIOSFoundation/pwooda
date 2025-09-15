package com.banya.neulpum.domain.entity

data class AppSettings(
    val notifications: NotificationSettings = NotificationSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val language: String = "ko",
    val autoLogin: Boolean = true,
    val biometricAuth: Boolean = false
)

data class NotificationSettings(
    val pushEnabled: Boolean = true,
    val chatNotifications: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "08:00"
)

data class AppearanceSettings(
    val theme: String = "dark",
    val fontSize: String = "medium",
    val colorScheme: String = "blue",
    val animationsEnabled: Boolean = true
)

data class PrivacySettings(
    val dataCollection: Boolean = true,
    val analytics: Boolean = true,
    val crashReports: Boolean = true,
    val locationSharing: Boolean = false
)

data class LegalDocument(
    val id: String,
    val title: String,
    val content: String,
    val version: String,
    val lastUpdated: String
)


