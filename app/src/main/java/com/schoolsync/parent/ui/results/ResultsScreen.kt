package com.schoolsync.parent.ui.results

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.ExamResult
import com.schoolsync.parent.data.model.SubjectResult
import com.schoolsync.parent.ui.theme.*

@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onPayFees: () -> Unit = {},
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Results",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // Pre-emptive dues warning — shown above the exam selector so
        // parents understand WHY a result might be withheld before they
        // tap into the exam list. Server still owns the actual block.
        if (uiState.pendingFees > 0.0) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                com.schoolsync.parent.ui.fees.FeeBlockedBanner(
                    dueAmount = uiState.pendingFees,
                    scope = "Result cards may be withheld until fees are cleared",
                    onPayClick = onPayFees
                )
            }
        }

        // Exam Selector
        if (uiState.examIds.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(12.dp)
                        .clickable { viewModel.toggleExamSelector() }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.examResult?.examName
                            ?: uiState.examIds.getOrNull(uiState.selectedExamIndex)
                            ?: "Select Exam",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Select Exam",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = uiState.examSelectorExpanded,
                    onDismissRequest = { viewModel.dismissExamSelector() },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    uiState.examIds.forEachIndexed { index, examId ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = examId,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            onClick = { viewModel.selectExam(index) }
                        )
                    }
                }
            }
        }

        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() }
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Teal, modifier = Modifier.size(40.dp))
                }
            } else if (uiState.examIds.isEmpty()) {
                EmptyResultsState()
            } else {
                val result = uiState.examResult

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Overall Result Card
                    if (result != null) {
                        item {
                            OverallResultCard(result = result)
                        }

                        // Subject Results Header
                        item {
                            Text(
                                text = "Subject-wise Marks",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Subject Results
                        itemsIndexed(result.subjects) { _, subject ->
                            SubjectResultCard(subject = subject)
                        }
                    }

                    // Error message
                    uiState.errorMessage?.let { error ->
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ErrorRedBg)
                                    .padding(12.dp)
                            ) {
                                Text(text = error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun OverallResultCard(result: ExamResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCardElevated(16.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pass/Fail Icon
        Icon(
            imageVector = if (result.isPassed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            tint = if (result.isPassed) SuccessGreen else ErrorRed,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (result.isPassed) "Passed" else "Failed",
            style = MaterialTheme.typography.titleLarge,
            color = if (result.isPassed) SuccessGreen else ErrorRed,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ResultStatColumn(
                label = "Percentage",
                value = "%.1f%%".format(result.percentage),
                color = when {
                    result.percentage >= 75 -> SuccessGreen
                    result.percentage >= 50 -> WarningAmber
                    else -> ErrorRed
                }
            )
            if (result.grade.isNotBlank()) {
                ResultStatColumn(
                    label = "Grade",
                    value = result.grade,
                    color = Teal
                )
            }
            ResultStatColumn(
                label = "Marks",
                value = "${result.totalMarks.toInt()}/${result.maxMarks.toInt()}",
                color = TextPrimary
            )
        }

        if (result.rank > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Rank: ${result.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    color = WarningAmber,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ResultStatColumn(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun SubjectResultCard(subject: SubjectResult) {
    val fraction = if (subject.maxMarks > 0) (subject.marksObtained / subject.maxMarks).toFloat() else 0f

    val progressColor = when {
        fraction >= 0.75f -> SuccessGreen
        fraction >= 0.50f -> WarningAmber
        fraction >= 0.33f -> InfoBlue
        else -> ErrorRed
    }

    val grade = if (subject.grade.isNotBlank()) subject.grade else when {
        fraction >= 0.90f -> "A+"
        fraction >= 0.80f -> "A"
        fraction >= 0.70f -> "B+"
        fraction >= 0.60f -> "B"
        fraction >= 0.50f -> "C"
        fraction >= 0.40f -> "D"
        fraction >= 0.33f -> "E"
        else -> "F"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grade circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(progressColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = grade,
                style = MaterialTheme.typography.titleSmall,
                color = progressColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subject.subjectName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${subject.marksObtained.toInt()}/${subject.maxMarks.toInt()}",
                    style = MaterialTheme.typography.titleSmall,
                    color = progressColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = GlassBorder,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun EmptyResultsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Results Available",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Results will appear here once exams are completed and graded.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
