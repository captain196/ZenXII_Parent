package com.schoolsync.parent.ui.fees

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.FeeHead
import com.schoolsync.parent.data.model.FeeOverview
import com.schoolsync.parent.data.model.FeePayment
import com.schoolsync.parent.data.model.FeeStructure
import com.schoolsync.parent.data.model.PendingFees
import com.schoolsync.parent.data.model.PendingMonth
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FeesTab(val title: String) {
    STRUCTURE("Fee Structure"),
    PENDING("Pending"),
    HISTORY("History"),
    MODULE_FEES("Module Fees"),
    CLEARANCE("Clearance")
}

data class FeesUiState(
    val isLoading: Boolean = true,
    val selectedTab: FeesTab = FeesTab.STRUCTURE,
    val overview: FeeOverview = FeeOverview(),
    val errorMessage: String? = null,
    val paymentInProgress: Boolean = false,
    val paymentIntentId: String? = null,
    val paymentIntentStatus: String? = null
)

@HiltViewModel
class FeesViewModel @Inject constructor(
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeesUiState())
    val uiState: StateFlow<FeesUiState> = _uiState.asStateFlow()

    init {
        loadFees()
    }

    fun selectTab(tab: FeesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadFees() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val studentId = user.userId
            val className = user.className
            val section = user.section

            if (studentId.isNotBlank() && className.isNotBlank() && section.isNotBlank()) {
                loadFeesFromFirestore(studentId, className, section)
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Student info not available")
                }
            }
        }
    }

    private suspend fun loadFeesFromFirestore(studentId: String, className: String, section: String) {
        try {
            // ── Fee structure ──
            val structureResult = feeFirestoreRepo.getFeeStructure(className, section)
            val feeStructure = structureResult.getOrNull()?.let { doc ->
                FeeStructure(
                    className = doc.className,
                    section = doc.section,
                    feeHeads = doc.feeHeads.map { head ->
                        FeeHead(
                            name = head.name,
                            amount = head.amount,
                            frequency = head.frequency
                        )
                    },
                    totalAnnualFee = doc.totalAnnualFee
                )
            } ?: FeeStructure()

            // ── Fee demands (pending) ──
            val demandsResult = feeFirestoreRepo.getFeeDemands(studentId)
            val pendingFees = demandsResult.getOrNull()?.let { demands ->
                val pendingMonths = demands.map { demand ->
                    PendingMonth(
                        month = demand.month,
                        amount = demand.netAmount,
                        dueDate = "",
                        status = when (demand.status) {
                            "paid" -> "Paid"
                            "partial" -> "Partial"
                            "overdue" -> "Overdue"
                            else -> "Pending"
                        }
                    )
                }
                val totalPending = demands
                    .filter { it.status != "paid" }
                    .sumOf { it.netAmount - it.paidAmount }
                PendingFees(
                    studentId = studentId,
                    totalPending = totalPending,
                    pendingMonths = pendingMonths
                )
            } ?: PendingFees()

            // ── Payment history ──
            val historyResult = feeFirestoreRepo.getPaymentHistory(studentId)
            val paymentHistory = historyResult.getOrNull()?.map { receipt ->
                FeePayment(
                    paymentId = receipt.id,
                    amount = receipt.amount,
                    date = receipt.createdAt?.toDate()?.let {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(it)
                    } ?: "",
                    month = receipt.feeMonths.joinToString(", "),
                    mode = receipt.paymentMode,
                    receiptNo = receipt.receiptNo,
                    remarks = receipt.remarks
                )
            } ?: emptyList()

            // ── Defaulter check ──
            val defaulterResult = feeFirestoreRepo.getDefaulterStatus(studentId)
            defaulterResult.getOrNull()?.let { defaulter ->
                // Log defaulter status for visibility (used in UI via overview.pendingFees)
                if (defaulter.examBlocked || defaulter.resultWithheld) {
                    Log.w("FeesVM", "Student is a defaulter: examBlocked=${defaulter.examBlocked}, resultWithheld=${defaulter.resultWithheld}")
                }
            }

            val overview = FeeOverview(
                feeStructure = feeStructure,
                pendingFees = pendingFees,
                paymentHistory = paymentHistory
            )

            _uiState.update { it.copy(isLoading = false, overview = overview) }
        } catch (e: Exception) {
            Log.e("FeesVM", "Failed to load fees from Firestore", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load fees data"
                )
            }
        }
    }

    fun refresh() {
        loadFees()
    }

    /**
     * Create a payment intent and observe its real-time status.
     */
    fun initiatePayment(months: List<String>) {
        val overview = _uiState.value.overview
        val totalAmount = overview.pendingFees.pendingMonths
            .filter { it.month in months }
            .sumOf { it.amount }
        if (totalAmount <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(paymentInProgress = true, paymentIntentStatus = null) }

            val user = tokenManager.user.firstOrNull() ?: User.empty()

            // Create payment intent in Firestore
            feeFirestoreRepo.createPaymentIntent(
                studentId = user.userId,
                studentName = user.name,
                amount = totalAmount,
                feeMonths = months
            ).fold(
                onSuccess = { intentId ->
                    _uiState.update {
                        it.copy(paymentIntentId = intentId, paymentIntentStatus = "requested")
                    }
                    // Payment intent created — admin/payment gateway will update status
                    // For now, mark as in progress until user refreshes
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            paymentInProgress = false,
                            errorMessage = e.message ?: "Failed to initiate payment"
                        )
                    }
                }
            )
        }
    }

    /**
     * Refresh fees after a successful payment.
     */
    fun onPaymentCompleted() {
        _uiState.update {
            it.copy(
                paymentInProgress = false,
                paymentIntentId = null,
                paymentIntentStatus = null
            )
        }
        loadFees()
    }
}
