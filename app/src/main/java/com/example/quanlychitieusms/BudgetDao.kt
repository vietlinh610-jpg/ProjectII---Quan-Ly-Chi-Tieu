package com.example.quanlychitieusms

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Đảm bảo data class này nằm ở đây nếu Linh đã xóa file riêng
data class BudgetProgress(
    val category: String,
    val limitAmount: Double,
    val spentAmount: Double
)

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(budget: Budget)

    @Query("""
        SELECT 
            b.category, 
            b.limitAmount, 
            COALESCE(SUM(t.amount), 0) as spentAmount
        FROM budgets b
        LEFT JOIN transactions t ON b.category = t.category 
            AND strftime('%m/%Y', t.timestamp / 1000, 'unixepoch') = b.monthYear
        WHERE b.monthYear = :monthYear
        GROUP BY b.category
    """)
    fun getBudgetProgress(monthYear: String): LiveData<List<BudgetProgress>>
}