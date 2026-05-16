package com.example.quanlychitieusms

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatActivity : AppCompatActivity() {

    private lateinit var repo: TransactionRepository
    private lateinit var chatDao: ChatDao
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var rvChat: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val db = AppDatabase.getDatabase(this)
        repo = TransactionRepository(db.transactionDao(), db.budgetDao())
        chatDao = db.chatDao()

        rvChat = findViewById(R.id.rvChat)
        val etInput = findViewById<EditText>(R.id.etChatInput)
        val btnSend = findViewById<ImageButton>(R.id.btnSend)
        val btnClear = findViewById<ImageButton>(R.id.btnClear)

        chatAdapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChat.adapter = chatAdapter

        // Load lịch sử chat
        lifecycleScope.launch {
            chatDao.getAllMessages().first().forEach {
                messages.add(ChatMessage(it.text, it.isAI))
            }
            if (messages.isEmpty()) {
                addMessage("Xin chào! Tôi có thể giúp bạn phân tích chi tiêu và hạn mức. Hãy hỏi tôi!", isAI = true, save = true)
            } else {
                chatAdapter.notifyDataSetChanged()
                rvChat.scrollToPosition(messages.size - 1)
            }
        }

        // Gợi ý câu hỏi nhanh
        setupQuickSuggestions()

        btnSend.setOnClickListener {
            val question = etInput.text.toString().trim()
            if (question.isEmpty()) return@setOnClickListener
            etInput.setText("")
            addMessage(question, isAI = false, save = true)
            askAI(question)
        }

        etInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && messages.isNotEmpty()) {
                rvChat.postDelayed({ rvChat.scrollToPosition(messages.size - 1) }, 300)
            }
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Xóa lịch sử chat
        btnClear.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Xóa lịch sử chat?")
                .setPositiveButton("Xóa") { _, _ ->
                    lifecycleScope.launch {
                        chatDao.clearHistory()
                        messages.clear()
                        chatAdapter.notifyDataSetChanged()
                        addMessage("Lịch sử đã được xóa. Tôi có thể giúp gì cho bạn?", isAI = true, save = true)
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    // Gợi ý câu hỏi nhanh
    private fun setupQuickSuggestions() {
        val suggestions = listOf(
            "Tháng này tôi tiêu bao nhiêu?",
            "Tôi có vượt hạn mức không?",
            "Danh mục nào tốn nhiều nhất?",
            "Giao dịch lớn nhất?"
        )
        val chipGroup = findViewById<LinearLayout>(R.id.chipGroup)
        suggestions.forEach { text ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_suggestion_chip, chipGroup, false) as TextView
            chip.text = text
            chip.setOnClickListener {
                addMessage(text, isAI = false, save = true)
                askAI(text)
            }
            chipGroup.addView(chip)
        }
    }

    private fun askAI(question: String) {
        addMessage("Đang phân tích...", isAI = true, save = false)
        val loadingIndex = messages.size - 1

        CoroutineScope(Dispatchers.IO).launch {
            val transactions = repo.allTransactions.first()
            val budgetSummary = getBudgetSummary(transactions)
            val summary = buildSummary(transactions, budgetSummary)
            val response = callGroqChat(question, summary)

            withContext(Dispatchers.Main) {
                messages[loadingIndex] = ChatMessage(response, isAI = true)
                chatAdapter.notifyItemChanged(loadingIndex)
                rvChat.scrollToPosition(messages.size - 1)
                // Lưu câu trả lời AI vào DB
                lifecycleScope.launch {
                    chatDao.insertMessage(ChatMessageEntity(text = response, isAI = true))
                }
            }
        }
    }

    private suspend fun getBudgetSummary(transactions: List<TransactionItem>): String {
        return try {
            val now = java.util.Calendar.getInstance()
            val month = String.format("%02d/%04d",
                now.get(java.util.Calendar.MONTH) + 1,
                now.get(java.util.Calendar.YEAR))

            // LiveData cần convert sang Flow hoặc dùng trực tiếp
            val budgetProgress = repo.getBudgetProgress(month).first()
            if (budgetProgress.isEmpty()) return "Chưa đặt hạn mức chi tiêu."

            budgetProgress.joinToString("\n") { b ->
                val percent = if (b.limitAmount > 0)
                    (b.spentAmount / b.limitAmount * 100).toInt() else 0
                "- ${b.category}: đã tiêu ${b.spentAmount.toLong()} / ${b.limitAmount.toLong()} VND ($percent%)" +
                        when {
                            percent >= 100 -> " ⚠️ VƯỢT HẠN MỨC"
                            percent >= 80  -> " ⚡ Gần đạt hạn mức"
                            else           -> ""
                        }
            }
        } catch (e: Exception) {
            "Không thể lấy hạn mức: ${e.message}"
        }
    }

    private fun buildSummary(transactions: List<TransactionItem>, budgetSummary: String): String {
        if (transactions.isEmpty()) return "Chưa có dữ liệu chi tiêu."

        val totalSpend = transactions.filter { it.amount < 0 }.sumOf { it.amount }
        val totalIncome = transactions.filter { it.amount > 0 }.sumOf { it.amount }
        val byCategory = transactions.filter { it.amount < 0 }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .entries.sortedBy { it.value }
            .joinToString("\n") { "- ${it.key}: ${it.value.toLong()} VND" }

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        val details = transactions.take(100).joinToString("\n") { tx ->
            val date = sdf.format(java.util.Date(tx.timestamp))
            val sign = if (tx.amount < 0) "" else "+"
            "[$date] ${tx.category} | ${tx.bankName} | $sign${tx.amount.toLong()} VND | ${tx.originalSms.take(50)}"
        }

        return """
=== TỔNG QUAN ===
Tổng chi tiêu: ${totalSpend.toLong()} VND
Tổng thu nhập: ${totalIncome.toLong()} VND
Số giao dịch: ${transactions.size}

=== HẠN MỨC CHI TIÊU ===
$budgetSummary

=== CHI TIÊU THEO DANH MỤC ===
$byCategory

=== CHI TIẾT GIAO DỊCH ===
$details
        """.trimIndent()
    }

    private suspend fun callGroqChat(question: String, summary: String): String =
        withContext(Dispatchers.IO) {
            try {
                val api = Retrofit.Builder()
                    .baseUrl("https://api.groq.com/openai/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build().create(GroqApiService::class.java)

                val request = GroqRequest(
                    model = "llama-3.1-8b-instant",
                    messages = listOf(
                        Message("system", """
Bạn là trợ lý tài chính cá nhân thông minh.
Dữ liệu chi tiêu và hạn mức của người dùng:
$summary

Nhiệm vụ:
- Phân tích chi tiêu dựa trên dữ liệu thực tế
- Cảnh báo nếu vượt hoặc gần đạt hạn mức (⚠️)
- Đưa ra lời khuyên tiết kiệm cụ thể
- Trả lời ngắn gọn, rõ ràng bằng tiếng Việt
- Nếu không đủ dữ liệu thì nói rõ
                        """.trimIndent()),
                        Message("user", question)
                    )
                )
                val response = api.classifyTransaction(
                    "Bearer ${BuildConfig.GROQ_API_KEY}", request)
                response.choices[0].message.content.trim()
            } catch (e: Exception) {
                "Xin lỗi, không thể trả lời lúc này: ${e.message}"
            }
        }

    private fun addMessage(text: String, isAI: Boolean, save: Boolean) {
        messages.add(ChatMessage(text, isAI))
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
        if (save) {
            lifecycleScope.launch {
                chatDao.insertMessage(ChatMessageEntity(text = text, isAI = isAI))
            }
        }
    }
}

data class ChatMessage(val text: String, val isAI: Boolean)