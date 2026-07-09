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
import androidx.compose.material.icons.filled.RemoveCircle
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

        // Exam Selector — MED-4: render human-readable exam names, not raw IDs.
        if (uiState.exams.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                val selectedName = uiState.selectedExam
                    ?.let { it.examName.ifBlank { it.id } }
                    ?: uiState.examResult?.examName
                    ?: "Select Exam"
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
                        text = selectedName,
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
                    uiState.exams.forEachIndexed { index, exam ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = exam.examName.ifBlank { exam.id },
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
            } else if (uiState.exams.isEmpty()) {
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
    // MED-3: an absentee must never be rendered as a genuine "0% / Failed".
    // HIGH-2: pass/fail/absent is resolved by the pure, unit-tested helper.
    val status = resolveResultStatus(
        passFail = result.passFail,
        percentage = result.percentage,
        absent = result.absent,
        passingPercent = result.passingPercent
    )
    // LOW-8: don't convey status by icon+colour alone — give the icon a real
    // content description and label the whole card for screen readers.
    val statusLabel = when (status) {
        ResultStatus.ABSENT -> "Absent"
        ResultStatus.PASS -> "Passed"
        ResultStatus.FAIL -> "Failed"
    }
    val statusColor = when (status) {
        ResultStatus.ABSENT -> TextSecondary
        ResultStatus.PASS -> SuccessGreen
        ResultStatus.FAIL -> ErrorRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCardElevated(16.dp)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Icon
        Icon(
            imageVector = when (status) {
                ResultStatus.ABSENT -> Icons.Filled.RemoveCircle
                ResultStatus.PASS -> Icons.Filled.CheckCircle
                ResultStatus.FAIL -> Icons.Filled.Cancel
            },
            contentDescription = "Result: $statusLabel",
            tint = statusColor,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleLarge,
            color = statusColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val pct = result.percentage
            ResultStatColumn(
                label = "Percentage",
                // MED-3: show "—" for an absentee instead of a fabricated 0.0%.
                value = formatOverallPercentage(pct),
                color = when {
                    pct == null -> TextSecondary
                    pct >= 75 -> SuccessGreen
                    pct >= 50 -> WarningAmber
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
                    contentDescription = "Rank ${result.rank}",
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
    // MED-3: absent subject → no marks, no progress, neutral chip.
    val isAbsent = subject.absent || subject.marksObtained == null
    val marks = subject.marksObtained
    val fraction = if (!isAbsent && subject.maxMarks > 0 && marks != null)
        (marks / subject.maxMarks).toFloat() else 0f

    val progressColor = when {
        isAbsent -> TextSecondary
        fraction >= 0.75f -> SuccessGreen
        fraction >= 0.50f -> WarningAmber
        fraction >= 0.33f -> InfoBlue
        else -> ErrorRed
    }

    // MED-5: NEVER synthesize a grade band client-side — the admin owns grading
    // and a fabricated band can disagree with the report card. Show "—" when
    // the writer left the grade blank (or the student was absent → "AB").
    val gradeLabel = subjectGradeLabel(subject.grade, marks, subject.absent)

    // LOW-8: describe the whole row + the progress bar for screen readers.
    val marksText = formatSubjectMarks(marks, subject.maxMarks, subject.absent)
    val subjectStateDesc = when {
        isAbsent -> "Absent"
        else -> "${marks?.toInt() ?: 0} out of ${subject.maxMarks.toInt()} marks"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .padding(14.dp)
            .semantics {
                contentDescription = "${subject.subjectName}: $subjectStateDesc" +
                    (if (subject.passFail.isNotBlank()) ", ${subject.passFail}" else "")
            },
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
                text = gradeLabel,
                style = MaterialTheme.typography.titleSmall,
                color = progressColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subject.subjectName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = marksText,
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
                    .clip(RoundedCornerShape(3.dp))
                    .semantics { stateDescription = subjectStateDesc },
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
