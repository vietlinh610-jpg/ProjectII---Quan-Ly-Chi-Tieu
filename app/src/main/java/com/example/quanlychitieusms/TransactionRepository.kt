package com.example.quanlychitieusms



import android.util.Log
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.asFlow

import kotlinx.coroutines.withContext

import androidx.lifecycle.LiveData

import retrofit2.Retrofit
import kotlinx.coroutines.flow.Flow

import retrofit2.converter.gson.GsonConverterFactory
import com.example.quanlychitieusms.BuildConfig




class TransactionRepository(private val transactionDao: TransactionDao, private val budgetDao: BudgetDao) {



    private val retrofit = Retrofit.Builder()

        .baseUrl("https://api.groq.com/openai/")

        .addConverterFactory(GsonConverterFactory.create())

        .build()



    private val groqApi = retrofit.create(GroqApiService::class.java)

    private val apiKey = BuildConfig.GROQ_API_KEY



    val allTransactions = transactionDao.getAllTransactions()

    val totalByCategory: LiveData<List<CategorySum>> = transactionDao.getExpenseByCategory()



    suspend fun insert(item: TransactionItem) {

        Log.d("GROQ_DEBU", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        Log.d("GROQ_DEBU", "SMS nhận được : ${item.originalSms}")



        // Nếu người dùng tự chọn danh mục mặc định lưu

        val validCategories = listOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn")

        if (validCategories.contains(item.category)) {

            Log.d("GROQ_DEBU", " Danh mục thủ công: ${item.category}")

            transactionDao.insertTransaction(

                item.copy(bankName = extractBankName(item.originalSms))

            )

            return

        }



        val rawSms = item.originalSms.lowercase().trim()

        Log.d("GROQ_DEBU", "SMS lowercase : $rawSms")



        // Bộ lọc thủ công

        val manualCategory = when {

            rawSms.contains("grab") || rawSms.contains("be ") ||

                    rawSms.contains("xanh sm") || rawSms.contains("taxi")

                -> "Di chuyển"



            rawSms.contains("shopee") || rawSms.contains("lazada") ||

                    rawSms.contains("tiki")

                -> "Mua sắm"



            rawSms.contains("highlands") || rawSms.contains("coffee") ||

                    rawSms.contains("food")

                -> "Ăn uống"



            rawSms.contains("tien dien") || rawSms.contains("tien nuoc") ||

                    rawSms.contains("evn") || rawSms.contains("vnpt") ||

                    rawSms.contains("viettel") || rawSms.contains("vinaphone") ||

                    rawSms.contains("internet") || rawSms.contains("hoa don")||

                    rawSms.contains("tra cuoc") || rawSms.contains("cuoc phi")

                -> "Hóa đơn"



            else -> null

        }



        Log.d("GROQ_DEBU", "Manual filter : ${manualCategory ?: "null → gọi AI"}")



        val finalCategory = manualCategory ?: askGroqAI(item.originalSms)



        Log.d("GROQ_DEBU", "Danh mục cuối : $finalCategory")

        Log.d("GROQ_DEBU", "Bank name     : ${extractBankName(item.originalSms)}")

        Log.d("GROQ_DEBU", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")



        transactionDao.insertTransaction(

            item.copy(

                category = finalCategory,

                bankName = extractBankName(item.originalSms)

            )

        )

    }



    suspend fun update(transaction: TransactionItem) {

        transactionDao.updateTransaction(transaction)

    }


    fun getBudgetProgress(month: String): Flow<List<BudgetProgress>> {
        return budgetDao.getBudgetProgress(month)
    }

    suspend fun saveBudget(budget: Budget) {
        budgetDao.insertOrUpdate(budget)
    }


    suspend fun delete(transaction: TransactionItem) {

        transactionDao.deleteTransaction(transaction)

    }



    fun getExpenseByCategoryAndTime(start: Long, end: Long): LiveData<List<CategorySum>> {

        return transactionDao.getExpenseByCategoryAndTime(start, end)

    }



    fun getYearlyStatistics(year: String): LiveData<List<MonthlySum>> {

        return transactionDao.getYearlyStatistics(year)

    }



    fun getAllYearsStatistics(): LiveData<List<YearlySummary>> {

        return transactionDao.getAllYearsStatistics()

    }



    private suspend fun askGroqAI(originalSms: String): String = withContext(Dispatchers.IO) {

        try {

            val contentPart = when {

                originalSms.contains("Noi dung:", ignoreCase = true) ->

                    originalSms.substringAfter("Noi dung:").substringBefore(". so du")

                originalSms.contains("ND:", ignoreCase = true) ->

                    originalSms.substringAfter("ND:").substringBefore("So du:")

                else -> originalSms

            }.trim()



            // Log nội dung gửi lên AI

            Log.d("GROQ_DEBU", "── Gọi AI ──────────────────────")

            Log.d("GROQ_DEBU", "Nội dung tách : $contentPart")



            val request = GroqRequest(

                model = "llama-3.1-8b-instant",

                messages = listOf(

                    Message("system", "Bạn là trợ lý phân loại chi tiêu. Chỉ trả về 1 trong các từ: Ăn uống, Mua sắm, Di chuyển, Hóa đơn, Khác. Không giải thích thêm."),

                    Message("user", "Phân loại giao dịch này: $contentPart")

                )

            )



            // Thay thế dòng gọi API cũ
            val response = groqApi.classifyTransaction("Bearer ${BuildConfig.GROQ_API_KEY}", request)

            val result = response.choices[0].message.content.trim()



            Log.d("GROQ_DEBU", "AI trả về     : $result")



            val validCategories = listOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn")

            validCategories.find { result.contains(it, ignoreCase = true) } ?: "Khác"



        } catch (e: retrofit2.HttpException) {

            val errorBody = e.response()?.errorBody()?.string()

            Log.e("GROQ_DEBU", "Lỗi HTTP ${e.code()}: $errorBody")

            "Khác"

        } catch (e: Exception) {

            Log.e("GROQ_DEBU", "Lỗi AI: ${e.message}")

            "Khác"

        }

    }



    private fun extractBankName(sms: String): String {

        val text = sms.uppercase()

        return when {

            text.contains("BIDV")                               -> "BIDV"

            text.contains("VIETCOMBANK") || text.contains("VCB") -> "Vietcombank"

            text.contains("TECHCOMBANK") || text.contains("TCB") -> "Techcombank"

            text.contains("MBBANK")                             -> "MB Bank"

            text.contains("VPBANK")                             -> "VPBank"

            text.contains("AGRIBANK")                           -> "Agribank"

            text.contains("VIETINBANK") || text.contains("CTG") -> "Vietinbank"

            text.contains("ACB")                                -> "ACB"

            text.contains("SACOMBANK")                          -> "Sacombank"

            text.contains("TPBANK")                             -> "TPBank"

            text.contains("MOMO")                               -> "MoMo"

            text.contains("ZALOPAY")                            -> "ZaloPay"

            else                                                -> "Ngân hàng"

        }

    }


}