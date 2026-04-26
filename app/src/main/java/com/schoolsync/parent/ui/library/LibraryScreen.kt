package com.schoolsync.parent.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.firestore.LibraryBookDoc
import com.schoolsync.parent.data.model.firestore.LibraryFineDoc
import com.schoolsync.parent.data.model.firestore.LibraryIssueDoc
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

// ─── Main Entry Point ───────────────────────────────────────────────────────

@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(11.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Library",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Tab Chips ───────────────────────────────────────────────────
        LibraryTabChips(
            selectedTab = uiState.selectedTab,
            onTabChange = viewModel::selectTab,
            currentBooksCount = uiState.currentBooks.size,
            historyCount = uiState.bookHistory.size,
            finesCount = uiState.fines.size
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Content ─────────────────────────────────────────────────────
        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() }
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
                }
            } else {
                when (uiState.selectedTab) {
                    0 -> CurrentBooksTab(books = uiState.currentBooks)
                    1 -> HistoryTab(history = uiState.bookHistory)
                    2 -> FinesTab(fines = uiState.fines, totalFines = uiState.totalFines)
                    3 -> CatalogTab(
                        books = uiState.catalogBooks,
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = viewModel::updateSearchQuery
                    )
                }
            }
        }

        // ── Error ───────────────────────────────────────────────────────
        uiState.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.errorBg)
                    .padding(12.dp)
            ) {
                Text(text = error, color = c.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TAB CHIPS
// ═══════════════════════════════════════════════════════════════════════════════

private data class LibTabDef(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

private val libraryTabs = listOf(
    LibTabDef(0, "My Books", Icons.AutoMirrored.Filled.MenuBook),
    LibTabDef(1, "History", Icons.Filled.History),
    LibTabDef(2, "Fines", Icons.Filled.Receipt),
    LibTabDef(3, "Catalog", Icons.Filled.Search)
)

@Composable
private fun LibraryTabChips(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    currentBooksCount: Int,
    historyCount: Int,
    finesCount: Int
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        libraryTabs.forEach { tab ->
            val isActive = selectedTab == tab.index
            val count = when (tab.index) {
                0 -> currentBooksCount
                1 -> historyCount
                2 -> finesCount
                else -> null
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isActive) {
                            Modifier.background(c.accent)
                        } else {
                            Modifier
                                .background(Color.Transparent)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(50))
                        }
                    )
                    .clickable { onTabChange(tab.index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (isActive) Color.White else c.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = tab.label,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) Color.White else c.textSecondary
                )
                if (count != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isActive) Color.White.copy(alpha = 0.25f)
                                else c.glass
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$count",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isActive) Color.White else c.textSecondary
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CURRENT BOOKS TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CurrentBooksTab(books: List<LibraryIssueDoc>) {
    if (books.isEmpty()) {
        EmptyState(
            emoji = "\uD83D\uDCDA",
            title = "No books currently borrowed",
            subtitle = "Visit the school library to borrow books"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(books, key = { it.id }) { book ->
                IssuedBookCard(book = book)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun IssuedBookCard(book: LibraryIssueDoc) {
    val c = LocalAppColors.current
    val daysLeft = LibraryViewModel.daysUntilDue(book.dueDate)
    val dueDateText = LibraryViewModel.dueDateLabel(book.dueDate)

    // Color based on urgency
    val urgencyColor = when {
        daysLeft == null -> c.textSecondary
        daysLeft < 0 -> c.error       // overdue
        daysLeft <= 1 -> c.error       // due today / tomorrow
        daysLeft <= 3 -> c.warning     // due soon
        else -> c.success              // plenty of time
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
    ) {
        // Left urgency bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(90.dp)
                .background(urgencyColor)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Book icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\uD83D\uDCD6", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.bookTitle.ifBlank { "Untitled Book" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Issued: ${LibraryViewModel.formatDisplayDate(book.issueDate)}",
                    fontSize = 10.sp,
                    color = c.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Due date pill
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DotPill(
                        text = dueDateText,
                        dotColor = urgencyColor,
                        bgColor = urgencyColor.copy(alpha = 0.15f),
                        textColor = urgencyColor
                    )

                    if (book.renewals > 0) {
                        DotPill(
                            text = "Renewed ${book.renewals}x",
                            dotColor = c.info,
                            bgColor = c.infoBg,
                            textColor = c.info
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  HISTORY TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryTab(history: List<LibraryIssueDoc>) {
    if (history.isEmpty()) {
        EmptyState(
            emoji = "\uD83D\uDCDA",
            title = "No borrowing history",
            subtitle = "Your borrowing history will appear here"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(history, key = { it.id }) { item ->
                HistoryBookCard(item = item)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HistoryBookCard(item: LibraryIssueDoc) {
    val c = LocalAppColors.current

    val statusColor = when (item.status.lowercase()) {
        "returned" -> c.success
        "issued" -> c.warning
        "overdue" -> c.error
        else -> c.textSecondary
    }
    val statusLabel = when (item.status.lowercase()) {
        "returned" -> "Returned"
        "issued" -> "Issued"
        "overdue" -> "Overdue"
        else -> item.status.replaceFirstChar { it.uppercase() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Book icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(statusColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (item.status.lowercase()) {
                    "returned" -> "\u2705"
                    "overdue" -> "\u26A0\uFE0F"
                    else -> "\uD83D\uDCD6"
                },
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.bookTitle.ifBlank { "Untitled Book" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))

            // Date range
            val dateRange = buildString {
                append(LibraryViewModel.formatDisplayDate(item.issueDate))
                append(" \u2192 ")
                if (item.returnDate.isNotBlank()) {
                    append(LibraryViewModel.formatDisplayDate(item.returnDate))
                } else {
                    append("--")
                }
            }
            Text(
                text = dateRange,
                fontSize = 10.sp,
                color = c.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status badge
            DotPill(
                text = statusLabel,
                dotColor = statusColor,
                bgColor = statusColor.copy(alpha = 0.15f),
                textColor = statusColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  FINES TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FinesTab(fines: List<LibraryFineDoc>, totalFines: Double) {
    val c = LocalAppColors.current

    if (fines.isEmpty()) {
        EmptyState(
            emoji = "\uD83C\uDF89",
            title = "No outstanding fines",
            subtitle = "You're all clear! Keep returning books on time"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Total fines summary card
            item {
                TotalFinesSummary(totalFines = totalFines, count = fines.size)
            }

            items(fines, key = { it.id }) { fine ->
                FineCard(fine = fine)
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TotalFinesSummary(totalFines: Double, count: Int) {
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.error.copy(alpha = 0.08f))
            .border(1.dp, c.error.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TOTAL OUTSTANDING",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = c.textTertiary,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "\u20B9${String.format("%.2f", totalFines)}",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = c.error
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$count pending fine${if (count != 1) "s" else ""}",
            fontSize = 11.sp,
            color = c.textSecondary
        )
    }
}

@Composable
private fun FineCard(fine: LibraryFineDoc) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
    ) {
        // Left red bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(80.dp)
                .background(c.error)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fine icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\uD83D\uDCB8", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fine.bookTitle.ifBlank { "Library Fine" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = fine.reason.replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    color = c.textSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                DotPill(
                    text = "Pending",
                    dotColor = c.error,
                    bgColor = c.errorBg,
                    textColor = c.error
                )
            }

            // Fine amount
            Text(
                text = "\u20B9${String.format("%.0f", fine.fineAmount)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = c.error
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CATALOG TAB
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CatalogTab(
    books: List<LibraryBookDoc>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    val c = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.glass)
                .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = c.textPrimary,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { keyboardController?.hide() }
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search by book title...",
                            fontSize = 14.sp,
                            color = c.textTertiary
                        )
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (searchQuery.length < 2) {
            EmptyState(
                emoji = "\uD83D\uDD0D",
                title = "Search the library catalog",
                subtitle = "Type at least 2 characters to search"
            )
        } else if (books.isEmpty()) {
            EmptyState(
                emoji = "\uD83D\uDCDA",
                title = "No books found",
                subtitle = "Try a different search term"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(books, key = { it.id }) { book ->
                    CatalogBookCard(book = book)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun CatalogBookCard(book: LibraryBookDoc) {
    val c = LocalAppColors.current

    val availabilityColor = when {
        book.availableCopies > 2 -> c.success
        book.availableCopies > 0 -> c.warning
        else -> c.error
    }
    val availabilityText = when {
        book.availableCopies > 0 -> "${book.availableCopies} available"
        else -> "Not available"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Book icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.purple.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83D\uDCD5", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title.ifBlank { "Untitled" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))

            // Author(s)
            val authorsText = book.authors.joinToString(", ").ifBlank { "Unknown Author" }
            Text(
                text = authorsText,
                fontSize = 10.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (book.category.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.category,
                    fontSize = 9.sp,
                    color = c.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Availability badge
            DotPill(
                text = availabilityText,
                dotColor = availabilityColor,
                bgColor = availabilityColor.copy(alpha = 0.15f),
                textColor = availabilityColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DotPill(
    text: String,
    dotColor: Color,
    bgColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String
) {
    com.schoolsync.parent.ui.components.EmptyStatePro(
        emoji = emoji,
        title = title,
        description = subtitle,
    )
}
