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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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

    var showConductRoute by rememberSaveable { mutableStateOf(false) }
    if (showConductRoute) {
        ConductRouteDialog(onDismiss = { showConductRoute = false })
    }

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
                    label = viewModel::categoryLabelLocalized,
                    colors = c,
                    onSelect = { key ->
                        // "conduct" does NOT compose a ticket. v1 has no
                        // confidential lane, so a report about a staff member
                        // would land in the ordinary queue, attributed and
                        // readable by anyone holding the Support module — which
                        // can include the person being reported. Route to a
                        // person instead of advertising a discretion we cannot
                        // provide. See ConductContactViewModel.
                        if (key == CONDUCT_CATEGORY) showConductRoute = true
                        else viewModel.updateCategory(key)
                    }
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

                // R12 — say it plainly when a restart dropped the attachments.
                //
                // The Uris cannot be restored (a Photo Picker grant dies with the
                // process and is not persistable), so the honest alternative to
                // keeping the photos is telling the parent they are gone. Without
                // this the form restored its text and route and looked COMPLETE,
                // which read as "I never attached them" rather than "they were
                // dropped" — the restore disguised the loss.
                //
                // Placed directly above the strip, where the photos would have
                // been, so it occupies the space whose emptiness it explains.
                if (uiState.photosClearedByRestart > 0 && uiState.pickedImages.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.support_photos_cleared),
                        style = MaterialTheme.typography.bodySmall,
                        // warning, not error: the ticket is still sendable and the
                        // photos are re-attachable. c.error is reserved in this
                        // module for a failed action (SupportListScreen retry).
                        color = c.warning,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
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
    // R31 — selectable(), not clickable().
    //
    // These chips are a single-choice group and a category is REQUIRED to submit
    // (SupportViewModel.canSubmit), but selection was conveyed only visually:
    // background colour and font weight. Confirmed on device (SD-T3-002) — every
    // chip exposed clickable=true and NOTHING else: no checkable, no checked, no
    // selected. A TalkBack user could move through all ten categories and never
    // hear which one was chosen, on the one control they cannot skip.
    //
    // selectable(role = RadioButton) inside selectableGroup() is what makes the
    // state audible, and it replaces clickable() in the same modifier position so
    // the touch target and ripple bounds are unchanged.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.selectableGroup()
    ) {
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
                            .selectable(
                                selected = on,
                                role = Role.RadioButton,
                                onClick = { onSelect(key) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                // Keep the last odd row aligned rather than stretched.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Category id that routes to a person instead of composing a ticket. */
private const val CONDUCT_CATEGORY = "conduct"

/**
 * Where a concern about a member of staff actually goes.
 *
 * Writes nothing. Height-capped and scrollable so it survives a small screen.
 *
 * ORDER IS LOAD-BEARING: the contact and its Call/Email buttons come FIRST, the
 * explanation after. They used to sit at the BOTTOM of the scrolling body, and at
 * a 200% font scale the prose above them consumed the whole 380dp cap — so the
 * dialog told a parent "speak to the school's designated contact directly" and
 * then clipped away every means of doing it. Only Close survived, because
 * AlertDialog pins its button slot.
 *
 * Verified on an emulator at font_scale 2.0 before and after. The users most
 * likely to run large text are the ones least able to recover from a dialog that
 * hides its only action, and this is the POSH/POCSO-adjacent path — so the
 * ordering degrades in the right direction: scrolling now hides the CONTEXT,
 * never the ACTION.
 */
@Composable
private fun ConductRouteDialog(
    onDismiss: () -> Unit,
    viewModel: ConductContactViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val contact by viewModel.contact.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.bgMid,
        title = {
            Text(
                stringResource(R.string.conduct_route_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // The actionable part first — see the note above.
                when {
                    contact.isLoading -> Text(
                        stringResource(R.string.conduct_route_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )

                    contact.isEmpty -> Text(
                        stringResource(R.string.conduct_route_fallback),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary
                    )

                    else -> {
                        Text(
                            stringResource(R.string.conduct_route_contact_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary
                        )
                        if (contact.name.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                contact.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                        }
                        // R22 — say WHICH contact this is.
                        //
                        // The fallback chain (grievance officer -> principal ->
                        // school general) is deliberate and stays: requiring setup
                        // would break this screen for every school until an admin
                        // filled a field in. What was missing is that nobody was
                        // told which arm answered. A complaint about a staff member
                        // may be routed to the office that staff member works in,
                        // so a complainant is entitled to know whether they are
                        // calling a designated grievance officer or the front desk
                        // BEFORE they speak.
                        //
                        // Factual, not alarming — the number is still worth calling.
                        if (!contact.isDesignated) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.conduct_route_general_contact),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            if (contact.phone.isNotBlank()) {
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            // fromParts keeps the number OPAQUE — never re-parsed as a URI.
                                            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.phone, null))
                                        )
                                    }
                                }) { Text(stringResource(R.string.conduct_route_call)) }
                            }
                            if (contact.email.isNotBlank()) {
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            // Opaque ssp + a sanitised address: no mailto query params can be smuggled in.
                                            Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", contact.email, null))
                                                .putExtra(
                                                    Intent.EXTRA_SUBJECT,
                                                    context.getString(R.string.conduct_route_email_subject)
                                                )
                                        )
                                    }
                                }) { Text(stringResource(R.string.conduct_route_email)) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    stringResource(R.string.conduct_route_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.conduct_route_not_logged),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.conduct_route_close))
            }
        }
    )
}
