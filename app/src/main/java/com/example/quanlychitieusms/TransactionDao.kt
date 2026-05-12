package com.example.quanlychitieusms

import androidx.room.Dao
import androidx.lifecycle.LiveData
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete


import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Update
    suspend fun updateTransaction(transaction: TransactionItem)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionItem)
    // Thêm một giao dịch mới
    @Insert
    suspend fun insertTransaction(transaction: TransactionItem)

    //  Lấy toàn bộ danh sách giao dịch, sắp xếp thời gian mới nhất lên đầu
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionItem>>

    //  Tính tổng số tiền theo từng danh mục
    @Query("SELECT category, SUM(amount) as totalAmount FROM transactions GROUP BY category")
    fun getTotalByCategory(): Flow<List<CategoryTotal>>
    // lấy dữ liệu cho biểu đồ
    @Query("""
        SELECT category, SUM(ABS(amount)) as total 
        FROM transactions 
        WHERE amount < 0 
        GROUP BY category
    """)
    fun getExpenseByCategory(): LiveData<List<CategorySum>>

    @Query("""
    SELECT category, SUM(ABS(amount)) as total 
    FROM transactions 
    WHERE amount < 0 
    AND timestamp >= :startTime AND timestamp <= :endTime
    GROUP BY category
""")
    fun getExpenseByCategoryAndTime(startTime: Long, endTime: Long): LiveData<List<CategorySum>>

    @Query("""
    SELECT 
        strftime('%m', datetime(timestamp / 1000, 'unixepoch')) as month, 
        SUM(ABS(amount)) as total 
    FROM transactions 
    WHERE amount < 0 
    AND strftime('%Y', datetime(timestamp / 1000, 'unixepoch')) = :year
    GROUP BY month
    ORDER BY month ASC
""")
    fun getYearlyStatistics(year: String): LiveData<List<MonthlySum>>

    // Thêm vào interface TransactionDao
    @Query("""
    SELECT 
        strftime('%Y', datetime(timestamp / 1000, 'unixepoch')) as year, 
        SUM(ABS(amount)) as total 
    FROM transactions 
    WHERE amount < 0 
    GROUP BY year 
    ORDER BY year ASC
""")
    fun getAllYearsStatistics(): LiveData<List<YearlySummary>>
}

data class CategoryTotal(
    val category: String,
    val totalAmount: Double
)
data class CategorySum(
    val category: String,
    val total: Double
)
