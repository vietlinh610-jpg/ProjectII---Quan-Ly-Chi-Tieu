package com.example.quanlychitieusms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            // Ghép tất cả các phần của SMS lại thành 1 chuỗi hoàn chỉnh
            val body = messages.joinToString("") { it.messageBody }
            val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "Unknown"

            Log.d("SMS_CAPTURE", "Từ: $sender | Nội dung đầy đủ: $body")

            // Loại bỏ tin nhắn rác
            val trashKeywords = listOf("OTP", "Ma xac thuc", "Diem thuong", "QC", "Khuyen mai")
            if (trashKeywords.any { body.contains(it, ignoreCase = true) }) return

            // Lấy các tin nhắn thanh toán
            val bankKeywords = listOf("thanh toan", "tru", "-", "So du")
            if (bankKeywords.any { body.contains(it, ignoreCase = true) }) {
                Log.d("SMS", "Bắt được tin nhắn từ $sender: $body")
                analyzeAndSave(context, body)
            }
        }
    }

    private fun analyzeAndSave(context: Context, body: String) {
        val bodyLower = body.lowercase()
        Log.d("BANK_LOGIC", "Bắt đầu phân tích: $bodyLower")

        val amountRegex =
            "(?:tru|cong|bi tru|thanh toan|PS|duoc cong|nhan duoc|vua nhan|chuyen den|gd:|-)\\s*([0-9.,]+)".toRegex()
        val amountMatch = amountRegex.find(bodyLower)

        if (amountMatch != null) {
            val rawAmount = amountMatch.groupValues[1]
                .replace("[,.]".toRegex(), "")
                .toDoubleOrNull() ?: 0.0

            val positiveKeywords = listOf("nhan duoc", "vua nhan", "cong", "chuyen den", "hoan tien")
            val negativeKeywords = listOf("tru", "bi tru", "thanh toan", "phi", "chuyen khoan di")

            val finalAmount = when {
                positiveKeywords.any { bodyLower.contains(it) } -> rawAmount
                negativeKeywords.any { bodyLower.contains(it) } -> -rawAmount
                else -> -rawAmount
            }

            Log.d("BANK_LOGIC", "✅ SMS đầy đủ: '$body' | Số tiền: $finalAmount")

            val db = AppDatabase.getDatabase(context)
            val repo = TransactionRepository(db.transactionDao())

            CoroutineScope(Dispatchers.IO).launch {
                repo.insert(
                    TransactionItem(
                        amount = finalAmount,
                        originalSms = body,      // SMS gốc đầy đủ
                        category = "Chưa phân loại", // để Repository + AI tự xử lý
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } else {
            Log.d("BANK_LOGIC", "❌ Không tìm thấy số tiền trong SMS: $body")
        }
    }
}