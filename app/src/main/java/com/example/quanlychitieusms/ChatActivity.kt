package com.example.quanlychitieusms

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class ChatActivity : AppCompatActivity() {

    private lateinit var repo: TransactionRepository
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        setContentView(R.layout.activity_chat)

        val db = AppDatabase.getDatabase(this)
        repo = TransactionRepository(
            transactionDao = db.transactionDao(),
            budgetDao = db.budgetDao()  // thêm dòng này
        )

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        val etInput = findViewById<EditText>(R.id.etChatInput)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)

        chatAdapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter


        addMessage("Xin chào! Tôi có thể giúp bạn phân tích chi tiêu. Hãy hỏi tôi bất cứ điều gì!", isAI = true)

        btnSend.setOnClickListener {
            val question = etInput.text.toString().trim()
            if (question.isEmpty()) return@setOnClickListener
            etInput.setText("")
            addMessage(question, isAI = false)
            askAI(question, rvChat)
        }
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish() // đóng ChatActivity, quay về MainActivity
        }

        // Lắng nghe khi bàn phím mở/đóng → cuộn RecyclerView xuống cuối

    }

    private fun askAI(question: String, rvChat: RecyclerView) {
        addMessage("Đang phân tích...", isAI = true)
        val loadingIndex = messages.size - 1

        CoroutineScope(Dispatchers.IO).launch {
            // Flow dùng .first() thay vì .value
            val transactions = repo.allTransactions.first()
            val summary = buildSummary(transactions)
            val response = callGroqChat(question, summary)

            withContext(Dispatchers.Main) {
                messages[loadingIndex] = ChatMessage(response, isAI = true)
                chatAdapter.notifyItemChanged(loadingIndex)
                rvChat.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun buildSummary(transactions: List<TransactionItem>): String {
        if (transactions.isEmpty()) return "Chưa có dữ liệu chi tiêu."

        val totalSpend = transactions.filter { it.amount < 0 }.sumOf { it.amount }
        val totalIncome = transactions.filter { it.amount > 0 }.sumOf { it.amount }

        // Tổng theo danh mục
        val byCategory = transactions.filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedBy { it.value }
            .joinToString("\n") { "- ${it.key}: ${it.value.toLong()} VND" }

        // Chi tiết từng giao dịch
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val details = transactions.take(100)
            .joinToString("\n") { tx ->
                val date = sdf.format(java.util.Date(tx.timestamp))
                val sign = if (tx.amount < 0) "" else "+"
                "[$date] ${tx.category} | ${tx.bankName} | $sign${tx.amount.toLong()} VND | ${tx.originalSms.take(50)}"
            }

        return """
=== TỔNG QUAN ===
Tổng chi tiêu: ${totalSpend.toLong()} VND
Tổng thu nhập: ${totalIncome.toLong()} VND
Số giao dịch: ${transactions.size}

=== CHI TIÊU THEO DANH MỤC ===
$byCategory

=== CHI TIẾT 50 GIAO DỊCH GẦN NHẤT ===
$details
    """.trimIndent()
    }

    private suspend fun callGroqChat(question: String, summary: String): String =
        withContext(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://api.groq.com/openai/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(GroqApiService::class.java)
                val key = "Bearer ${BuildConfig.GROQ_API_KEY}"

                val request = GroqRequest(
                    model = "llama-3.1-8b-instant",
                    messages = listOf(
                        Message(
                            "system",
                            """Bạn là trợ lý tài chính cá nhân thông minh.
Dưới đây là toàn bộ dữ liệu chi tiêu thực tế của người dùng:

$summary

Định dạng mỗi giao dịch: [ngày giờ] danh mục | ngân hàng | số tiền | nội dung SMS

Hãy trả lời câu hỏi dựa trên dữ liệu trên.
Ví dụ người dùng hỏi:
- "tháng 4 tôi tiêu bao nhiêu?" → tính tổng các giao dịch tháng 4
- "tôi mua sắm những gì?" → liệt kê các giao dịch danh mục Mua sắm
- "giao dịch lớn nhất?" → tìm giao dịch có số tiền lớn nhất
- "hóa đơn viettel?" → tìm giao dịch có từ khóa viettel

Trả lời ngắn gọn, rõ ràng bằng tiếng Việt.
Nếu không tìm thấy dữ liệu liên quan thì nói rõ."""
                        ),
                        Message("user", question)
                    )
                )

                val response = api.classifyTransaction(key, request)
                response.choices[0].message.content.trim()
            } catch (e: Exception) {
                "Xin lỗi, tôi không thể trả lời lúc này. Lỗi: ${e.message}"
            }
        }

    private fun addMessage(text: String, isAI: Boolean) {
        messages.add(ChatMessage(text, isAI))
        chatAdapter.notifyItemInserted(messages.size - 1)
        findViewById<RecyclerView>(R.id.rvChat).scrollToPosition(messages.size - 1)
    }
}

data class ChatMessage(val text: String, val isAI: Boolean)