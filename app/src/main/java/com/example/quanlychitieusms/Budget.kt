package com.example.quanlychitieusms
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val limitAmount: Double,
    val monthYear: String
)