package com.schoolsync.parent.ui.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.FeeDemandDoc
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import com.schoolsync.parent.data.repository.firestore.TransportFeesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * TransportFeesScreen — F10 LC-21 P-G..H: transport-category fee demands
 * (pending + history). Payment is intentionally NOT wired here — parents
 * tap the Pay button in the existing Fees module screen; F10 reuses the
 * existing Razorpay checkout journey by design (no parallel payment
 * flow, no business-logic duplication). This screen is presentation-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportFeesScreen(
    onBack: () -> Unit,
    onGoToFeesModule: () -> Unit,
    viewModel: TransportFeesViewModel = hiltViewModel()
) {
    val state = viewModel.state.value
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transport fees") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { SectionHeader("Pending") }
                if (state.pending.isEmpty()) {
                    item { EmptyCard("No pending transport fees.") }
                } else {
                    items(state.pending, key = { it.id }) {
                        PendingRow(demand = it, onPay = onGoToFeesModule)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Payment history") }
                if (state.history.isEmpty()) {
                    item { EmptyCard("No transport fee payments yet.") }
                } else {
                    items(state.history, key = { it.id }) { HistoryRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun EmptyCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text, color = Color.Gray)
        }
    }
}

@Composable
private fun PendingRow(demand: FeeDemandDoc, onPay: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(demand.feeHead.ifBlank { "Transport fee" }, fontWeight = FontWeight.SemiBold)
                    Text(demand.period.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("INR ${demand.balance}", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onPay) { Text("Pay in Fees module →") }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(receipt: FeeReceiptDoc) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Receipt: ${receipt.receiptNo.ifBlank { receipt.id.take(12) }}", fontWeight = FontWeight.SemiBold)
            Text(
                "INR ${receipt.amount} · ${receipt.paymentMode.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

data class TransportFeesState(
    val loading: Boolean = true,
    val pending: List<FeeDemandDoc> = emptyList(),
    val history: List<FeeReceiptDoc> = emptyList()
)

@HiltViewModel
class TransportFeesViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val transportFeesRepo: TransportFeesRepository
) : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(TransportFeesState())
    val state: State<TransportFeesState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = TransportFeesState(loading = true)
            try {
                val userId = tokenManager.user.firstOrNull()?.userId ?: run {
                    _state.value = TransportFeesState(loading = false); return@launch
                }
                val pending = transportFeesRepo.getPendingTransportDemands(userId).getOrDefault(emptyList())
                val history = transportFeesRepo.getTransportReceipts(userId).getOrDefault(emptyList())
                _state.value = TransportFeesState(loading = false, pending = pending, history = history)
            } catch (e: Exception) {
                _state.value = TransportFeesState(loading = false)
            }
        }
    }
}
