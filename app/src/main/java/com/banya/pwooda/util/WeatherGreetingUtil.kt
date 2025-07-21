package com.banya.pwooda.util

import kotlin.random.Random

object WeatherGreetingUtil {
    
    private val weatherGreetings = listOf(
        "오늘 날씨가 정말 좋네요.",
        "맑은 하늘이 기분을 좋게 만들어요.",
        "따뜻한 햇살이 가득한 하루예요.",
        "상쾌한 바람이 불어오는 날씨네요.",
        "오늘은 외출하기 좋은 날씨예요.",
        "맑고 화창한 하루가 되길 바라요.",
        "기분 좋은 날씨가 계속되길 바라요.",
        "오늘도 좋은 하루 보내세요.",
        "상쾌한 아침이에요.",
        "오늘 하루도 힘내세요.",
        "좋은 하루 되세요.",
        "오늘도 즐거운 하루 보내세요.",
        "상쾌한 공기가 가득한 하루예요.",
        "오늘도 행복한 하루 되세요.",
        "기분 좋은 하루가 되길 바라요."
    )
    
    fun getRandomWeatherGreeting(): String {
        return weatherGreetings[Random.nextInt(weatherGreetings.size)]
    }
    
    fun getWelcomeMessage(): String {
        return getPersonalizedWelcomeMessage(null)
    }

    fun getPersonalizedWelcomeMessage(customerName: String?): String {
        val weatherGreeting = getRandomWeatherGreeting()
        return if (customerName != null && customerName.isNotEmpty()) {
            "안녕하세요. ${customerName} 고객님, $weatherGreeting 반가워요. 전 BGF 리테일의 AI 스탭, 리나 라고 합니다. 저에게 제품을 보여 주시거나 말을 걸어 주세요."
        } else {
            "안녕하세요. $weatherGreeting 반가워요. 전 BGF 리테일의 AI 스탭, 리나 라고 합니다. 저에게 제품을 보여 주시거나 말을 걸어 주세요."
        }
    }
}