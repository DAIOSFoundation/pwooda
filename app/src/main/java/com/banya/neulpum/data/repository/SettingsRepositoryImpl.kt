package com.banya.neulpum.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.banya.neulpum.domain.entity.AppSettings
import com.banya.neulpum.domain.entity.NotificationSettings
import com.banya.neulpum.domain.entity.AppearanceSettings
import com.banya.neulpum.domain.entity.PrivacySettings
import com.banya.neulpum.domain.entity.LegalDocument
import com.banya.neulpum.domain.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    override suspend fun getSettings(): AppSettings {
        val settingsJson = prefs.getString("app_settings", null)
        return if (settingsJson != null) {
            try {
                gson.fromJson(settingsJson, AppSettings::class.java)
            } catch (e: Exception) {
                getDefaultSettings()
            }
        } else {
            getDefaultSettings()
        }
    }
    
    override suspend fun updateSettings(settings: AppSettings): Boolean {
        return try {
            val settingsJson = gson.toJson(settings)
            prefs.edit().putString("app_settings", settingsJson).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun updateNotificationSettings(notificationSettings: NotificationSettings): Boolean {
        return try {
            val currentSettings = getSettings()
            val updatedSettings = currentSettings.copy(notifications = notificationSettings)
            updateSettings(updatedSettings)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun updateAppearanceSettings(appearanceSettings: AppearanceSettings): Boolean {
        return try {
            val currentSettings = getSettings()
            val updatedSettings = currentSettings.copy(appearance = appearanceSettings)
            updateSettings(updatedSettings)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun updatePrivacySettings(privacySettings: PrivacySettings): Boolean {
        return try {
            val currentSettings = getSettings()
            val updatedSettings = currentSettings.copy(privacy = privacySettings)
            updateSettings(updatedSettings)
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getLegalDocuments(): List<LegalDocument> {
        return listOf(
            LegalDocument(
                id = "privacy_policy",
                title = "개인정보처리방침",
                content = """
                    늘품 개인정보처리방침
                    
                    1. 개인정보의 수집 및 이용목적
                    - 서비스 제공 및 계정 관리
                    - 고객 지원 및 문의 응답
                    - 서비스 개선 및 신규 서비스 개발
                    
                    2. 수집하는 개인정보 항목
                    - 필수: 이메일, 이름
                    - 선택: 프로필 이미지, 위치 정보
                    
                    3. 개인정보의 보유 및 이용기간
                    - 회원 탈퇴 시까지 또는 법정 보유기간
                    
                    4. 개인정보의 제3자 제공
                    - 원칙적으로 제3자에게 제공하지 않음
                    - 법령에 따른 수사 목적으로 법령에 정해진 절차에 따라 수사기관의 요구가 있는 경우
                    
                    5. 이용자 및 법정대리인의 권리
                    - 개인정보 열람, 정정, 삭제, 처리정지 요구 가능
                    
                    6. 개인정보 보호책임자
                    - 이메일: privacy@neulpum.com
                    
                    최종 업데이트: 2024년 12월
                """.trimIndent(),
                version = "1.0",
                lastUpdated = "2024-12-01"
            ),
            LegalDocument(
                id = "terms_of_service",
                title = "이용약관",
                content = """
                    늘품 이용약관
                    
                    제1조 (목적)
                    이 약관은 늘품가 제공하는 AI 스마트 매장 안내 서비스의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.
                    
                    제2조 (정의)
                    1. "서비스"라 함은 늘품가 제공하는 AI 채팅, 제품 정보, 매장 안내 등의 서비스를 의미합니다.
                    2. "이용자"라 함은 이 약관에 따라 회사와 이용계약을 체결하고 회사가 제공하는 서비스를 이용하는 자를 의미합니다.
                    
                    제3조 (약관의 효력 및 변경)
                    1. 이 약관은 서비스 이용 시점부터 효력을 발생합니다.
                    2. 회사는 필요한 경우 이 약관을 변경할 수 있으며, 변경된 약관은 공지사항을 통해 공지합니다.
                    
                    제4조 (서비스의 제공)
                    1. 회사는 다음과 같은 서비스를 제공합니다:
                       - AI 채팅 서비스
                       - 제품 정보 조회
                       - 매장 안내 서비스
                       - 기타 회사가 정하는 서비스
                    
                    2. 서비스는 연중무휴, 1일 24시간 제공함을 원칙으로 합니다.
                    
                    제5조 (서비스 이용)
                    1. 이용자는 서비스를 이용할 때 다음 각 호의 행위를 하여서는 안 됩니다:
                       - 법령 또는 공서양속에 반하는 행위
                       - 타인의 권리나 명예, 신용 등을 침해하는 행위
                       - 서비스의 정상적인 운영을 방해하는 행위
                    
                    제6조 (회사의 의무)
                    1. 회사는 안정적이고 지속적인 서비스 제공을 위해 최선을 다합니다.
                    2. 회사는 이용자의 개인정보를 보호하기 위해 보안 시스템을 구축하고 개인정보처리방침을 공시하고 준수합니다.
                    
                    제7조 (이용자의 의무)
                    1. 이용자는 관계법령, 이 약관의 규정, 이용안내 및 서비스상에 공지한 주의사항을 준수해야 합니다.
                    2. 이용자는 서비스 이용을 통해 얻은 정보를 회사의 사전 승낙 없이 복사, 복제, 변경, 번역, 출판, 방송 및 기타의 방법으로 사용하거나 제3자에게 제공할 수 없습니다.
                    
                    제8조 (책임제한)
                    1. 회사는 천재지변 또는 이에 준하는 불가항력으로 인하여 서비스를 제공할 수 없는 경우에는 서비스 제공에 관한 책임이 면제됩니다.
                    2. 회사는 이용자의 귀책사유로 인한 서비스 이용의 장애에 대하여는 책임을 지지 않습니다.
                    
                    제9조 (분쟁해결)
                    1. 회사는 이용자가 제기하는 정당한 의견이나 불만을 반영하고 그 피해를 보상처리하기 위하여 피해보상처리기구를 설치, 운영합니다.
                    2. 회사와 이용자 간에 발생한 분쟁에 관하여는 대한민국 법을 적용합니다.
                    
                    최종 업데이트: 2024년 12월
                """.trimIndent(),
                version = "1.0",
                lastUpdated = "2024-12-01"
            ),
            LegalDocument(
                id = "data_processing",
                title = "데이터 처리 정책",
                content = """
                    늘품 데이터 처리 정책
                    
                    1. 데이터 수집 목적
                    - 서비스 제공 및 개선
                    - 사용자 경험 향상
                    - 보안 및 사기 방지
                    
                    2. 수집하는 데이터 유형
                    - 계정 정보: 이메일, 이름
                    - 사용 데이터: 채팅 기록, 제품 조회 이력
                    - 기술 데이터: 디바이스 정보, 앱 사용 통계
                    
                    3. 데이터 보안
                    - 암호화를 통한 데이터 보호
                    - 접근 권한 제한
                    - 정기적인 보안 감사
                    
                    4. 데이터 보존 기간
                    - 서비스 이용 기간 동안 보존
                    - 회원 탈퇴 시 30일 후 삭제
                    - 법적 요구사항에 따른 보존
                    
                    5. 데이터 권리
                    - 데이터 접근, 수정, 삭제 요청 가능
                    - 데이터 이전 요청 가능
                    - 처리 제한 요청 가능
                    
                    최종 업데이트: 2024년 12월
                """.trimIndent(),
                version = "1.0",
                lastUpdated = "2024-12-01"
            )
        )
    }
    
    override suspend fun getLegalDocument(id: String): LegalDocument? {
        return getLegalDocuments().find { it.id == id }
    }
    
    override suspend fun resetToDefaults(): Boolean {
        return try {
            val defaultSettings = getDefaultSettings()
            updateSettings(defaultSettings)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getDefaultSettings(): AppSettings {
        return AppSettings(
            notifications = NotificationSettings(),
            appearance = AppearanceSettings(),
            privacy = PrivacySettings(),
            language = "ko",
            autoLogin = true,
            biometricAuth = false
        )
    }
}
