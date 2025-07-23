package com.banya.pwooda.util

import kotlin.random.Random

object WeatherGreetingUtil {
    
    private val weatherGreetings = listOf(
        "오늘 날씨가 정말 좋아.",
        "난 오늘 맑은 하늘 때문에 기분이 좋아.",
        "따뜻한 햇살이 가득한 하루야.",
        "상쾌한 바람이 불어오는 날씨야.",
        "오늘은 외출하기 좋은 날씨야.",
        "맑고 화창한 하루가 되길 바래.",
        "기분 좋은 날씨가 계속되길 바래.",
        "오늘도 좋은 하루가 되렴.",
        "상쾌한 아침이야.",
        "오늘 하루도 힘내.",
        "오늘도 즐거운 하루 보내고 있지?",
        "상쾌한 공기가 가득한 하루야.",
        "오늘도 행복한 하루 보내고 있니?.",
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
            "${customerName} 야, $weatherGreeting 궁금한게 있으면 뭐든지 나한테 물어봐 줄래?"
        } else {
            "$weatherGreeting 궁금한게 있으면 뭐든지 나한테 물어봐 줄래?"
        }
    }
}