package com.banya.pwooda.service

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.*

class PaymentService {
    
    fun generateNaverPayQRCode(productName: String, price: Int): Bitmap {
        // 네이버페이 결제 URL 생성 (실제 네이버페이 API 연동 시 수정 필요)
        val paymentUrl = createNaverPayUrl(productName, price)
        
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(paymentUrl, BarcodeFormat.QR_CODE, 512, 512, hints)
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    private fun createNaverPayUrl(productName: String, price: Int): String {
        // 실제 네이버페이 연동 시에는 네이버페이 API를 사용해야 합니다
        // 현재는 예시 URL을 반환합니다
        return "https://pay.naver.com/payment?product=${productName}&amount=$price&method=naverpay"
    }
} 