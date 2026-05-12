package com.example.quanlychitieusms

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
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

class MainActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { TransactionRepository(database.transactionDao()) }

    private val viewModel: TransactionViewModel by viewModels {
        TransactionViewModelFactory(repository)
    }

    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Xin quyền đọc SMS
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1)
        }

        // Thiết lập Lịch sử
        val recyclerView = findViewById<RecyclerView>(R.id.rvTransactions)
        adapter = TransactionAdapter(emptyList()) { transaction ->
            showEditDeleteDialog(transaction)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        // thiết lập chức năng
        findViewById<MaterialButton>(R.id.fabAdd).setOnClickListener {
            showAddManualDialog()
        }

        val tvSeeAll = findViewById<TextView>(R.id.tvSeeAll)
        tvSeeAll.setOnClickListener {
            if (recyclerView.visibility == View.GONE) {
                recyclerView.visibility = View.VISIBLE
                tvSeeAll.text = "Thu gọn"
            } else {
                recyclerView.visibility = View.GONE
                tvSeeAll.text = "Xem tất cả"
            }
        }

        // bộ lọc chuyển đổi tháng/năm
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleViewMode)
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnMonthMode -> viewModel.setViewMode(ViewMode.MONTH)
                    R.id.btnYearMode -> viewModel.setViewMode(ViewMode.YEAR)
                    R.id.btnAllYearsMode -> viewModel.setViewMode(ViewMode.ALL_YEARS) // Thêm dòng này
                }
                updateDateLabel(viewModel.currentDate.value)
            }
        }

        //Điều chỉnh thời gian lùi/ tăng
        val tvMonthLabel = findViewById<TextView>(R.id.tvCurrentMonth)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = findViewById<ImageButton>(R.id.btnNextMonth)

        viewModel.currentDate.observe(this) { calendar ->
            val pattern = if (viewModel.viewMode.value == ViewMode.MONTH) "MMMM, yyyy" else "yyyy"
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale("vi", "VN"))
            tvMonthLabel.text = sdf.format(calendar.time).replaceFirstChar { it.uppercase() }
        }

        btnPrev.setOnClickListener { viewModel.changeDate(-1) }
        btnNext.setOnClickListener { viewModel.changeDate(1) }

        viewModel.chartData.observe(this) { data ->
            if (data != null) {
                updateBarChart(data)
            }
        }

        viewModel.allTransactions.observe(this) { transactions ->
            adapter.updateData(transactions)
        }
    }

    // Vẽ biểu đồ
    private fun updateBarChart(data: Any) {
        val barChart = findViewById<BarChart>(R.id.barChart)
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        val colors = ArrayList<Int>()
        val xAxis = barChart.xAxis

        when (viewModel.viewMode.value) {
            ViewMode.YEAR -> {
                //xử lý dữ liệu năm
                val yearlyData = data as? List<MonthlySum> ?: emptyList()
                for (i in 0..11) {
                    val monthStr = String.format("%02d", i + 1)
                    val amount = yearlyData.find { it.month == monthStr }?.total ?: 0.0
                    entries.add(BarEntry(i.toFloat(), amount.toFloat()))
                    labels.add("T${i + 1}")
                    colors.add(Color.parseColor("#1A73E8"))
                }
                xAxis.labelCount = 12
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = 11.5f
            }

            ViewMode.ALL_YEARS -> {
                // xử lý dữ liệu tổng chi tiêu
                val yearlySummary = data as? List<YearlySummary> ?: emptyList()
                yearlySummary.forEachIndexed { index, item ->
                    entries.add(BarEntry(index.toFloat(), item.total.toFloat()))
                    labels.add(item.year)
                    colors.add(Color.parseColor("#4CAF50"))
                }
                xAxis.labelCount = yearlySummary.size
                xAxis.axisMinimum = -0.5f // Rất quan trọng để không bị mất nửa cột đầu
                xAxis.axisMaximum = if (yearlySummary.isEmpty()) 0.5f else yearlySummary.size - 0.5f
            }

            else -> {
                // xử lý chế độ tháng
                val monthlyData = data as? List<CategorySum> ?: emptyList()
                val allCategories = listOf("Ăn uống", "Mua sắm", "Di chuyển", "Hóa đơn", "Khác")
                val colorCodes = listOf("#FF9800", "#E91E63", "#4CAF50", "#2196F3", "#9E9E9E")
                val dataMap = monthlyData.associateBy({ it.category }, { it.total })

                allCategories.forEachIndexed { index, name ->
                    val amount = dataMap[name] ?: 0.0
                    entries.add(BarEntry(index.toFloat(), amount.toFloat()))
                    labels.add(name)
                    colors.add(Color.parseColor(colorCodes[index]))
                }
                xAxis.labelCount = allCategories.size
                xAxis.axisMinimum = -0.5f
                xAxis.axisMaximum = allCategories.size - 0.5f
            }
        }

        val dataSet = BarDataSet(entries, "Chi tiêu (VND)")
        dataSet.colors = colors
        dataSet.valueTextSize = 10f

        barChart.data = BarData(dataSet)

        // cấu hình giao diện chiều ngang
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.axisMinimum = 0f
        barChart.animateY(800)
        barChart.invalidate()
    }

    // Các hàm thêm sưa xóa dữ liệu
    private fun showAddManualDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
        val etContent = dialogView.findViewById<EditText>(R.id.etContent)
        val spCategory = dialogView.findViewById<Spinner>(R.id.spCategory)
        val layoutSelectDate = dialogView.findViewById<View>(R.id.layoutSelectDate)
        val tvSelectedDate = dialogView.findViewById<TextView>(R.id.tvSelectedDate)

        val selectedCalendar = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        tvSelectedDate.text = "Ngày: ${sdf.format(selectedCalendar.time)}"

        layoutSelectDate.setOnClickListener {
            android.app.DatePickerDialog(this, { _, y, m, d ->
                selectedCalendar.set(y, m, d)
                tvSelectedDate.text = "Ngày: ${sdf.format(selectedCalendar.time)}"
            }, selectedCalendar.get(java.util.Calendar.YEAR), selectedCalendar.get(java.util.Calendar.MONTH), selectedCalendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
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
            .setItems(categories) { _, which ->
                viewModel.update(transaction.copy(category = categories[which]))
            }.show()
    }

    private fun updateDateLabel(calendar: java.util.Calendar?) {
        val cal = calendar ?: return
        val tvMonthLabel = findViewById<TextView>(R.id.tvCurrentMonth)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = findViewById<ImageButton>(R.id.btnNextMonth)

        when (viewModel.viewMode.value) {
            ViewMode.MONTH -> {
                val sdf = java.text.SimpleDateFormat("MMMM, yyyy", java.util.Locale("vi", "VN"))
                tvMonthLabel.text = sdf.format(cal.time).replaceFirstChar { it.uppercase() }
                btnPrev.visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
            }
            ViewMode.YEAR -> {
                tvMonthLabel.text = "Năm ${cal.get(java.util.Calendar.YEAR)}"
                btnPrev.visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
            }
            ViewMode.ALL_YEARS -> {
                tvMonthLabel.text = "Tổng chi tiêu các năm"
                // ẩn nút lùi khiở chế độ tất cả
                btnPrev.visibility = View.GONE
                btnNext.visibility = View.GONE
            }else -> {
            tvMonthLabel.text = "Không xác định"
        }
        }
    }
}