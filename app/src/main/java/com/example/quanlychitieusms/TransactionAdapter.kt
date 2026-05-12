package com.example.quanlychitieusms

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private var list: List<TransactionItem>,
    private val onLongClick: (TransactionItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class DisplayItem {
        data class Header(val dateTitle: String) : DisplayItem()
        data class Transaction(val data: TransactionItem) : DisplayItem()
    }

    private var displayList: List<DisplayItem> = buildDisplayList(list)

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_TRANSACTION = 1
    }

    // --- ViewHolder header ngày ---
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDateHeader: TextView = view.findViewById(R.id.tvDateHeader)
    }

    // --- ViewHolder giao dịch ---
    class TransactionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCategoryIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvTimeAndSource: TextView = view.findViewById(R.id.tvTimeAndSource)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvSmsRaw: TextView = view.findViewById(R.id.tvSmsRaw)
    }

    // --- Build danh sách gồm cả header ngày ---
    private fun buildDisplayList(transactions: List<TransactionItem>): List<DisplayItem> {
        val result = mutableListOf<DisplayItem>()
        val keySdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val displaySdf = SimpleDateFormat("EEEE, dd 'tháng' M", Locale("vi"))
        var lastDateKey = ""

        for (item in transactions) {
            val date = Date(item.timestamp)
            val dateKey = keySdf.format(date)
            if (dateKey != lastDateKey) {
                result.add(DisplayItem.Header(displaySdf.format(date)))
                lastDateKey = dateKey
            }
            result.add(DisplayItem.Transaction(item))
        }
        return result
    }

    override fun getItemViewType(position: Int) = when (displayList[position]) {
        is DisplayItem.Header -> VIEW_TYPE_HEADER
        is DisplayItem.Transaction -> VIEW_TYPE_TRANSACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_date_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            TransactionViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = displayList[position]) {

            is DisplayItem.Header -> {
                (holder as HeaderViewHolder).tvDateHeader.text = item.dateTitle
            }

            is DisplayItem.Transaction -> {
                val h = holder as TransactionViewHolder
                val tx = item.data

                // Tên danh mục
                h.tvCategory.text = tx.category

                // Giờ + bankName
                val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeStr = timeSdf.format(Date(tx.timestamp))
                h.tvTimeAndSource.text = "$timeStr · ${tx.bankName}"

                // SMS gốc
                h.tvSmsRaw.text = tx.originalSms

                // Số tiền
                val dec = DecimalFormat("#,###")
                h.tvAmount.text = "${dec.format(tx.amount)} VND"

                // Màu số tiền
                val color = if (tx.amount < 0)
                    android.R.color.holo_red_dark
                else
                    android.R.color.holo_green_dark
                h.tvAmount.setTextColor(ContextCompat.getColor(h.itemView.context, color))

                // Icon + màu nền theo danh mục
                val (iconRes, bgColor) = getCategoryStyle(tx.category)
                h.ivCategoryIcon.setImageResource(iconRes)
                h.ivCategoryIcon.setBackgroundColor(
                    ContextCompat.getColor(h.itemView.context, bgColor)
                )

                // Long click
                h.itemView.setOnLongClickListener {
                    onLongClick(tx)
                    true
                }
            }
        }
    }

    override fun getItemCount() = displayList.size

    fun updateData(newList: List<TransactionItem>) {
        list = newList
        displayList = buildDisplayList(newList)
        notifyDataSetChanged()
    }

    // Map danh mục → (icon, màu nền)
    private fun getCategoryStyle(category: String): Pair<Int, Int> {
        val cat = category.lowercase()
        return when {
            cat.contains("ăn") || cat.contains("food") || cat.contains("nhà hàng") || cat.contains("cafe")
                -> Pair(R.drawable.ic_food, R.color.bg_food)

            cat.contains("mua") || cat.contains("shop") || cat.contains("quần") || cat.contains("áo")
                -> Pair(R.drawable.ic_shopping, R.color.bg_shopping)

            cat.contains("di chuyển") || cat.contains("grab") || cat.contains("xe") || cat.contains("xăng")
                -> Pair(R.drawable.ic_transport, R.color.bg_transport)

            cat.contains("hóa đơn")
                -> Pair(R.drawable.ic_bill, R.color.bg_bill)

            else -> Pair(R.drawable.ic_other, R.color.bg_other)
        }
    }
}