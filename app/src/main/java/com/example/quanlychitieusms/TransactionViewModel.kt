package com.example.quanlychitieusms

import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale


enum class ViewMode { MONTH, YEAR, ALL_YEARS }

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val _currentDate = MutableLiveData(Calendar.getInstance())
    val currentDate: LiveData<Calendar> = _currentDate

    private val _viewMode = MutableLiveData(ViewMode.MONTH)
    val viewMode: LiveData<ViewMode> = _viewMode

    val allTransactions: LiveData<List<TransactionItem>> = repository.allTransactions.asLiveData()

    private var lastSource: LiveData<*>? = null
    val chartData = MediatorLiveData<Any>()
    // Thêm vào TransactionViewModel.kt
    val budgetProgress: LiveData<List<BudgetProgress>> = currentDate.switchMap { cal ->
        val monthYear = SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(cal.time)
        repository.getBudgetProgress(monthYear)
    }

    // Thêm hàm này để lưu ngân sách
    fun saveBudget(budget: Budget) = viewModelScope.launch {
        repository.saveBudget(budget)
    }

    init {
        chartData.addSource(_currentDate) { updateChartSource() }
        chartData.addSource(_viewMode) { updateChartSource() }
    }


    private fun updateChartSource() {
        val cal = _currentDate.value ?: return
        val mode = _viewMode.value ?: return

        lastSource?.let { chartData.removeSource(it) }

        val newSource: LiveData<*> = when (mode) {
            ViewMode.MONTH -> {
                val start = cal.clone() as Calendar
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                val end = cal.clone() as Calendar
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                end.set(Calendar.HOUR_OF_DAY, 23)
                repository.getExpenseByCategoryAndTime(start.timeInMillis, end.timeInMillis)
            }
            ViewMode.YEAR -> {
                repository.getYearlyStatistics(cal.get(Calendar.YEAR).toString())
            }
            ViewMode.ALL_YEARS -> {
                repository.getAllYearsStatistics()
            }
        }

        lastSource = newSource
        chartData.addSource(newSource) { data ->
            chartData.value = data
        }
    }

    fun changeDate(offset: Int) {
        val nextCal = _currentDate.value?.clone() as Calendar
        when (_viewMode.value) {
            ViewMode.YEAR -> nextCal.add(Calendar.YEAR, offset)
            ViewMode.MONTH -> nextCal.add(Calendar.MONTH, offset)
            else -> return // Không làm gì ở chế độ ALL_YEARS
        }
        _currentDate.value = nextCal
    }

    fun setViewMode(mode: ViewMode) {
        if (_viewMode.value != mode) _viewMode.value = mode
    }

    // CRUD
    fun insert(transaction: TransactionItem) = viewModelScope.launch { repository.insert(transaction) }
    fun update(transaction: TransactionItem) = viewModelScope.launch { repository.update(transaction) }
    fun delete(transaction: TransactionItem) = viewModelScope.launch { repository.delete(transaction) }
    fun getBudgetProgress(month: String) = repository.getBudgetProgress(month)



}

class TransactionViewModelFactory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}