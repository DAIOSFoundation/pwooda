package com.banya.pwooda.data

data class User(
    val id: String,
    val nickname: String,
    val age: Int,
    val disability_level: String,
    val interests: List<String>,
    val dislikes: List<String>,
    val communication_style: String,
    val calming_triggers: List<String>,
    val warning_signs: List<String>,
    val goal: String? = null,
    val motivation: String? = null,
    val feedback: List<String>? = null
)

data class DailyScheduleItem(
    val time: String,
    val label: String,
    val type: String
)

data class WeeklySchedule(
    val id: String,
    val schedule: Map<String, List<DailyScheduleItem>>
)

data class Medication(
    val id: String,
    val med_name: String,
    val dosage: String,
    val frequency: String,
    val how_to_take: String,
    val side_effects: List<String>,
    val emergency_action: String
)

data class EducatorMaterials(
    val dailyLivingSkills: List<String>,
    val socialInteractionEtiquette: List<String>,
    val safetyAndSelfProtection: List<String>,
    val socialScenarioGames: List<String>,
    val behaviorImprovementTips: List<String>
)

data class PwoodaData(
    val users: List<User>,
    val weeklySchedules: List<WeeklySchedule>,
    val medications: List<Medication>,
    val educatorMaterials: EducatorMaterials
)