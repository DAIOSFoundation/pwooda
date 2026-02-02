package com.banya.neulpum

import android.app.Application
import android.content.Context

class AppContextHolder : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
    companion object {
        @JvmStatic
        var appContext: Context? = null
            private set
    }
}


