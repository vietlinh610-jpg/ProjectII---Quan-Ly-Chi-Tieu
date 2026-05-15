package com.example.quanlychitieusms


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionItem::class, Budget::class], // Thêm Budget::class ở đây
    version = 3, // Tăng lên 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration cũ (1 -> 2) để thêm cột bankName
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE transactions ADD COLUMN bankName TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // Migration MỚI (2 -> 3) để tạo bảng ngân sách
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets (" +
                            "category TEXT NOT NULL PRIMARY KEY, " +
                            "limitAmount REAL NOT NULL, " +
                            "monthYear TEXT NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_manager_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Thêm MIGRATION_2_3 vào đây
                    // .fallbackToDestructiveMigration() // Nếu Linh không sợ mất dữ liệu cũ, mở dòng này ra cho nhanh
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}