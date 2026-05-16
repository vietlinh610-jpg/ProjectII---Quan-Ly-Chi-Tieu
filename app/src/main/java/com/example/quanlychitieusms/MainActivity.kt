package com.example.quanlychitieusms

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import android.text.InputType
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { TransactionRepository(database.transactionDao(), database.budgetDao()) }
    private val viewModel: TransactionViewModel by viewModels { TransactionViewModelFactory(repository) }
    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Cấp quyền SMS
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1)
        }

        // 2. Thiết lập RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.rvTransactions)
        adapter = TransactionAdapter(emptyList()) { transaction -> showEditDeleteDialog(transaction) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 3. Thiết lập các nút bấm nổi (FAB)
        findViewById<MaterialButton>(R.id.fabAdd).setOnClickListener { showAddManualDialog() }
        findViewById<FloatingActionButton>(R.id.fabBudget).setOnClickListener { showBudgetManagementDialog() }

        // 4. Các sự kiện điều khiển (Xem tất cả, Toggle chế độ)
        val tvSeeAll = findViewById<TextView>(R.id.tvSeeAll)
        tvSeeAll.setOnClickListener {
            recyclerView.visibility = if (recyclerView.visibility == View.GONE) View.VISIBLE else View.GONE
            tvSeeAll.text = if (recyclerView.visibility == View.VISIBLE) "Thu gọn" else "Xem tất cả"
        }

        findViewById<MaterialButtonToggleGroup>(R.id.toggleViewMode).addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnMonthMode -> viewModel.setViewMode(ViewMode.MONTH)
                    R.id.btnYearMode -> viewModel.setViewMode(ViewMode.YEAR)
                    R.id.btnAllYearsMode -> viewModel.setViewMode(ViewMode.ALL_YEARS)
                }
                updateDateLabel(viewModel.currentDate.value)
            }
        }
        val fabChat = findViewById<TextView>(R.id.fabChat)

        fabChat.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_MOVE -> {
                    view.x = event.rawX - view.width / 2
                    view.y = event.rawY - view.height / 2
                    true
                }
                else -> false
            }
        }

        fabChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnPrevMonth).setOnClickListener { viewModel.changeDate(-1) }
        findViewById<ImageButton>(R.id.btnNextMonth).setOnClickListener { viewModel.changeDate(1) }

        // 5. Lắng nghe dữ liệu (Observers)
        viewModel.currentDate.observe(this) { calendar ->
            updateDateLabel(calendar)
            checkBudgetAlert(calendar)
        }

        viewModel.chartData.observe(this) { spentData ->
            updateBarChart(spentData, viewModel.budgetProgress.value ?: emptyList())
        }

        viewModel.budgetProgress.observe(this) { budgetData ->
            updateBarChart(viewModel.chartData.value ?: emptyList<Any>(), budgetData)
        }

        viewModel.allTransactions.observe(this) { transactions -> adapter.updateData(transactions) }
    }

    // --- QUẢN LÝ HẠN MỨC (BUDGET) ---

    private fun showBudgetManagementDialog() {
        val calendar = viewModel.currentDate.value ?: Calendar.getInstance()
        val monthStr = SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
        val progressList = viewModel.budgetProgress.value ?: emptyList()
        val categories = arrayOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn", "Khác")

        val displayList = categories.map { catName ->
            val limit = progressList.find { item->
                item.category == catName }?.limitAmount ?: 0.0
            "$catName: ${String.format("%,.0f", limit)} VND"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Hạn mức tháng $monthStr")
            .setItems(displayList) { _, which -> showSetBudgetDialog(categories[which]) }
            .setPositiveButton("Đóng", null).show()
    }

    private fun showSetBudgetDialog(category: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Nhập số tiền (VND)..."
        }
        AlertDialog.Builder(this)
            .setTitle("Đặt ngân sách: $category")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val amount = input.text.toString().toDoubleOrNull() ?: 0.0
                val calendar = viewModel.currentDate.value ?: Calendar.getInstance()
                val monthYear = SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
                viewModel.saveBudget(Budget(category, amount, monthYear))
                Toast.makeText(this, "Đã cập nhật hạn mức!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null).show()
    }

    // --- VẼ BIỂU ĐỒ CỘT ĐÔI ---

    private fun updateBarChart(data: Any, budgetData: List<BudgetProgress>) {
        val barChart = findViewById<BarChart>(R.id.barChart)
        val xAxis = barChart.xAxis
        val categories = listOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn", "Khác")
        val labels = ArrayList<String>()

        if (viewModel.viewMode.value == ViewMode.MONTH) {
            val monthlyData = data as? List<CategorySum> ?: emptyList()
            val entriesSpent = ArrayList<BarEntry>()
            val entriesLimit = ArrayList<BarEntry>()
            val spentColors = ArrayList<Int>()

            val spentMap = monthlyData.associateBy({ it.category }, { it.total })
            val limitMap = budgetData.associateBy({ it.category }, { it.limitAmount })

            categories.forEachIndexed { i, name ->
                val spentValue = Math.abs(spentMap[name] ?: 0.0)
                val limitValue = limitMap[name] ?: 0.0

                entriesSpent.add(BarEntry(i.toFloat(), spentValue.toFloat()))
                entriesLimit.add(BarEntry(i.toFloat(), limitValue.toFloat()))
                labels.add(name)

                // --- LOGIC ĐỔI MÀU Ở ĐÂY ---
                if (limitValue > 0 && spentValue > limitValue) {
                    // Nếu tiêu quá hạn mức: Đổi sang màu Đỏ
                    spentColors.add(Color.RED)
                } else {
                    // Nếu vẫn trong hạn mức: Giữ màu Xanh mặc định
                    spentColors.add(Color.parseColor("#1A73E8"))
                }
            }

            val setSpent = BarDataSet(entriesSpent, "Thực tế").apply {
                colors = spentColors // Gán danh sách màu động
                valueTextSize = 10f
            }

            val setLimit = BarDataSet(entriesLimit, "Hạn mức").apply {
                color = Color.parseColor("#D3D3D3") // Hạn mức vẫn để màu xám nhẹ
                valueTextSize = 10f
            }
            // --- CÔNG THỨC CĂN CHỈNH "TỈ LỆ VÀNG" ---
            val groupSpace = 0.3f    // Khoảng cách giữa các nhóm
            val barSpace = 0.05f     // Khoảng cách giữa 2 cột trong 1 nhóm
            val barWidth = 0.3f      // Độ rộng của mỗi cột
            // Tổng: (0.3 + 0.05) * 2 + 0.3 = 1.0 (Bắt buộc tổng phải là 1.0 để nhãn nằm giữa)

            val barData = BarData(setSpent, setLimit)
            barData.barWidth = barWidth
            barChart.data = barData

            // Chỉ định điểm bắt đầu và các khoảng cách
            barChart.groupBars(0f, groupSpace, barSpace)

            xAxis.setCenterAxisLabels(true)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = categories.size.toFloat()
            xAxis.granularity = 1f // Đảm bảo nhãn hiện đúng từng bước 1
        } else {
            // Logic cho Năm/Tất cả (Vẽ cột đơn)
            val entries = ArrayList<BarEntry>()
            if (viewModel.viewMode.value == ViewMode.YEAR) {
                val yearlyData = data as? List<MonthlySum> ?: emptyList()
                for (i in 0..11) {
                    val amount = yearlyData.find { it.month == String.format("%02d", i + 1) }?.total ?: 0.0
                    entries.add(BarEntry(i.toFloat(), Math.abs(amount).toFloat()))
                    labels.add("T${i + 1}")
                }
            }
            barChart.data = BarData(BarDataSet(entries, "Chi tiêu").apply { color = Color.parseColor("#1A73E8") })
            xAxis.setCenterAxisLabels(false)
        }

        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)

        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.axisMinimum = 0f
        barChart.animateY(800)
        barChart.invalidate()
    }

    private fun checkBudgetAlert(calendar: Calendar) {
        val monthYear = SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(calendar.time)
        viewModel.getBudgetProgress(monthYear).observe(this) { progressList ->
            progressList.forEach { p ->
                if (p.spentAmount > p.limitAmount && p.limitAmount > 0) {
                    Toast.makeText(this, "⚠️ Vượt hạn mức ${p.category}!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- CÁC HÀM PHỤ TRỢ (Dọn dẹp) ---

    private fun updateDateLabel(calendar: Calendar?) {
        val cal = calendar ?: return
        val tvMonthLabel = findViewById<TextView>(R.id.tvCurrentMonth)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = findViewById<ImageButton>(R.id.btnNextMonth)

        when (viewModel.viewMode.value) {
            ViewMode.MONTH -> {
                tvMonthLabel.text = SimpleDateFormat("MMMM, yyyy", Locale("vi", "VN")).format(cal.time).replaceFirstChar { it.uppercase() }
                btnPrev.visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
            }
            ViewMode.YEAR -> {
                tvMonthLabel.text = "Năm ${cal.get(Calendar.YEAR)}"
                btnPrev.visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
            }
            ViewMode.ALL_YEARS -> {
                tvMonthLabel.text = "Tổng chi tiêu các năm"
                btnPrev.visibility = View.GONE
                btnNext.visibility = View.GONE
            }
            else -> { // Thêm cái này để xử lý trường hợp null hoặc các giá trị khác
                tvMonthLabel.text = "Không xác định"
            }
        }
    }

    private fun showAddManualDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etContent = dialogView.findViewById<EditText>(R.id.etContent)
        val spCategory = dialogView.findViewById<Spinner>(R.id.spCategory)
        val layoutSelectDate = dialogView.findViewById<View>(R.id.layoutSelectDate)
        val tvSelectedDate = dialogView.findViewById<TextView>(R.id.tvSelectedDate)

        val selectedCalendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        tvSelectedDate.text = "Ngày: ${sdf.format(selectedCalendar.time)}"

        layoutSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                selectedCalendar.set(y, m, d)
                tvSelectedDate.text = "Ngày: ${sdf.format(selectedCalendar.time)}"
            }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(
                Calendar.DAY_OF_MONTH)).show()
        }

        val categories = arrayOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn", "Khác")
        spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        AlertDialog.Builder(this)
            .setTitle("Thêm chi tiêu mới")
            .setView(dialogView)
            .setPositiveButton("Lưu") { _, _ ->
                val amountStr = etAmount.text.toString()
                val content = etContent.text.toString()
                if (amountStr.isNotEmpty() && content.isNotEmpty()) {
                    viewModel.insert(TransactionItem(
                        amount = -(amountStr.toDouble()),
                        originalSms = content,
                        category = spCategory.selectedItem.toString(),
                        timestamp = selectedCalendar.timeInMillis,
                        bankName = "Thủ công"
                    ))
                }
            }.setNegativeButton("Hủy", null).show()
    }

    private fun showEditDeleteDialog(transaction: TransactionItem) {
        AlertDialog.Builder(this).setTitle("Tùy chọn")
            .setItems(arrayOf("Sửa danh mục", "Xóa giao dịch")) { _, which ->
                if (which == 0) showEditCategoryDialog(transaction) else viewModel.delete(transaction)
            }.show()
    }

    private fun showEditCategoryDialog(transaction: TransactionItem) {
        val categories = arrayOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn", "Khác")
        AlertDialog.Builder(this).setTitle("Chọn danh mục đúng")
            .setItems(categories) { _, which -> viewModel.update(transaction.copy(category = categories[which])) }.show()
    }
}