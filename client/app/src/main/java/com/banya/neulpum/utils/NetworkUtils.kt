package com.banya.neulpum.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {
    /**
     * 네트워크 연결 상태를 확인합니다.
     * @return true: 연결됨, false: 연결 안됨
     */
    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * 네트워크 예외인지 확인합니다.
     */
    fun isNetworkException(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("network") ||
                message.contains("unreachable") ||
                message.contains("timeout") ||
                message.contains("connection") ||
                message.contains("failed to connect") ||
                message.contains("no route to host") ||
                exception is java.net.UnknownHostException ||
                exception is java.net.ConnectException ||
                exception is java.net.SocketTimeoutException
    }
}

