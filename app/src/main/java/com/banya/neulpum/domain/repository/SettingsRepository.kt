package com.banya.neulpum.domain.repository

import com.banya.neulpum.domain.entity.AppSettings
import com.banya.neulpum.domain.entity.LegalDocument
import com.banya.neulpum.domain.entity.NotificationSettings
import com.banya.neulpum.domain.entity.AppearanceSettings
import com.banya.neulpum.domain.entity.PrivacySettings

interface SettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(settings: AppSettings): Boolean
    suspend fun updateNotificationSettings(settings: NotificationSettings): Boolean
    suspend fun updateAppearanceSettings(settings: AppearanceSettings): Boolean
    suspend fun updatePrivacySettings(settings: PrivacySettings): Boolean
    suspend fun getLegalDocuments(): List<LegalDocument>
    suspend fun getLegalDocument(id: String): LegalDocument?
    suspend fun resetToDefaults(): Boolean
}
