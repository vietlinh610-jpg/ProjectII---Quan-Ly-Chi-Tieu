package com.example.quanlychitieusms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,           // Số tiền chi tiêu
    val originalSms: String,      // Nội dung SMS gốc
    val category: String,         // Danh mục
    val timestamp: Long,           // Thời gian giao dịch
    val bankName: String = ""      // Nguồn giao dịch
)