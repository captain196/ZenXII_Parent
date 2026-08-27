package com.schoolsync.parent.ui.support

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.parent.R
import com.schoolsync.parent.data.repository.firestore.SupportFirestoreRepository
import com.schoolsync.parent.ui.theme.*

/**
 * Raise a ticket.
 *
 * There is deliberately NO student picker. This install logs a parent in AS the
 * student — `userId` is the student id and the User model carries no children
 * list — so there is nothing to choose between. An earlier draft of the plan
 * specified a mandatory picker; the app's actual identity model made it moot.
 *
 * Layout follows the app's local rule that forms must survive small screens and
 * landscape: the body scrolls, the submit bar is pinned, and imePadding keeps
 * the keyboard off the field being typed into.
 */
@Composable
fun SupportComposeScreen(
    onBack: () -> Unit,
    onSent: (String) -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current
    val scroll = rememberScrollState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(SupportFirestoreRepository.MAX_ATTACHMENTS)
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    // Navigate away only once the write is durable — server-acked or queued.
    LaunchedEffect(uiState.submittedTicketId) {
        uiState.submittedTicketId?.let { id ->
            viewModel.consumeSubmitted()
            onSent(id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = c.textPrimary)
                }
                Text(
                    stringResource(R.string.support_raise),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            uiState.errorMessage?.let { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp)).background(c.errorBg).padding(12.dp)
                ) {
                    Text(msg, color = c.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Scrolling body — the form must fit a short screen in landscape.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    stringResource(R.string.support_pick_category),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                // Categories are a fixed, ZenXii-defined catalogue so counts stay
                // comparable across schools. Hostel is absent on purpose — it has
                // its own module and its own warden inbox.
                FlowCategoryPicker(
                    categories = viewModel.categories,
                    selected = uiState.category,
                    label = viewModel::categoryLabel,
                    colors = c,
                    onSelect = viewModel::updateCategory
                )

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = uiState.subject,
                    onValueChange = viewModel::updateSubject,
                    label = { Text(stringResource(R.string.support_subject)) },
                    placeholder = { Text(stringResource(R.string.support_subject_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.body,
                    onValueChange = viewModel::updateBody,
                    label = { Text(stringResource(R.string.support_body)) },
                    placeholder = { Text(stringResource(R.string.support_body_hint)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = uiState.pickedImages.size < SupportFirestoreRepository.MAX_ATTACHMENTS
                    ) { Text(stringResource(R.string.support_add_photo)) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.support_photos_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }

                if (uiState.pickedImages.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.pickedImages) { uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp))
                                )
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.support_remove_photo),
                                    tint = c.textPrimary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(c.surfaceDark)
                                        .clickable { viewModel.removeImage(uri) }
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }

            // Pinned footer — never scrolls out of reach on a small screen.
            Surface(color = c.surfaceElevated, tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    Button(
                        onClick = viewModel::submit,
                        enabled = uiState.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent)
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                                color = c.textPrimary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.support_sending))
                        } else {
                            Text(stringResource(R.string.support_send))
                        }
                    }
                }
            }
        }
    }
}

/** Simple wrapping chip row. Avoids pulling in an experimental FlowRow. */
@Composable
private fun FlowCategoryPicker(
    categories: List<String>,
    selected: String,
    label: (String) -> String,
    colors: AppColors,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val on = key == selected
                    Text(
                        text = label(key),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (on) colors.textPrimary else colors.textSecondary,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) colors.accentBg else colors.surfaceDark)
                            .clickable { onSelect(key) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                // Keep the last odd row aligned rather than stretched.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
