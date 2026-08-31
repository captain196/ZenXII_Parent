package com.schoolsync.parent.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.AssistantMessage
import com.schoolsync.parent.ui.theme.LocalAppColors

/**
 * "Ask ZenXii" — the student/parent assistant.
 *
 * Layout rules this screen must keep (dialogs and chat screens clipping on
 * small phones and in landscape is a recurring bug class in these apps):
 *   · the thread is the only scrolling region and it fills the free space
 *   · the composer is pinned and carries imePadding, so the keyboard pushes it
 *     rather than covering it
 *   · nothing has a fixed height that could exceed a short landscape viewport
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    /** route, drafted subject, drafted details — the assistant prepared these. */
    onOpenSupport: (String, String, String) -> Unit,
    vm: AssistantViewModel = hiltViewModel(),
) {
    val c = LocalAppColors.current
    val ui by vm.ui.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Keep the newest turn in view as the thread grows or the model is thinking.
    LaunchedEffect(ui.messages.size, ui.isThinking) {
        val n = ui.messages.size + if (ui.isThinking) 1 else 0
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    Scaffold(
        containerColor = c.bgMid,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.assistant_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.textPrimary,
                        )
                        Text(
                            if (ui.isThinking) stringResource(R.string.assistant_thinking)
                            else stringResource(R.string.assistant_subtitle),
                            fontSize = 11.5.sp,
                            color = c.textSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = c.textSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bgStart),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding(),          // composer rides above the keyboard
        ) {
            if (ui.unavailableReason != null) {
                UnavailableNotice(ui.unavailableReason!!)
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)          // the ONLY scrolling region
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.messages.isEmpty()) item { Intro() }
                items(ui.messages) { m -> MessageRow(m, onOpenSupport) }
                if (ui.isThinking) item { ThinkingBubble() }
            }

            if (ui.messages.isEmpty()) SuggestionChips(onPick = vm::send)

            Composer(
                value = ui.input,
                enabled = !ui.isThinking,
                onChange = vm::onInputChange,
                onSend = { vm.send() },
            )
        }
    }
}

@Composable
private fun Intro() {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.assistant_intro_title),
            fontSize = 17.sp, fontWeight = FontWeight.Medium, color = c.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.assistant_intro_body),
            fontSize = 13.sp, color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(10.dp))
        // Required disclosure: the person must know this is an AI, not staff.
        Text(
            stringResource(R.string.assistant_ai_disclosure),
            fontSize = 11.sp, color = c.textTertiary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

@Composable
private fun MessageRow(m: AssistantMessage, onOpenSupport: (String, String, String) -> Unit) {
    val c = LocalAppColors.current
    val mine = m.role == AssistantMessage.Role.USER

    Column(Modifier.fillMaxWidth()) {
        // What the server actually looked at — shown so an answer about a
        // child's records is visibly sourced from the school's own data.
        if (m.toolsUsed.isNotEmpty() && !mine) {
            ToolChip(m.toolsUsed)
            Spacer(Modifier.height(5.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                color = when {
                    m.isError -> c.errorBg
                    mine -> c.chatSent
                    else -> c.chatReceived
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (mine) 16.dp else 5.dp,
                    bottomEnd = if (mine) 5.dp else 16.dp,
                ),
                border = if (mine) null
                else androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    m.text,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    fontSize = 14.2.sp,
                    lineHeight = 20.sp,
                    color = when {
                        m.isError -> c.error
                        mine -> c.onBanner
                        else -> c.textPrimary
                    },
                )
            }
        }

        // Safety affordance. The helpline reaches the student as model prose, so
        // it cannot be a typed field — but a distressed child should not have to
        // copy digits out of a chat bubble. If the number is present, offer to dial.
        if (!mine && m.text.contains(TELE_MANAS_NUMBER)) {
            val ctx = LocalContext.current
            Spacer(Modifier.height(7.dp))
            FilledTonalButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$TELE_MANAS_NUMBER"))
                    )
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = c.errorBg, contentColor = c.error,
                ),
            ) {
                Text(
                    stringResource(R.string.assistant_call_telemanas),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }

        // The Support Desk handoff. The assistant never files a ticket — this
        // sends the student to the screen that does, where the rules cap and
        // reporter identity still apply.
        val route = m.handoffRoute
        if (route != null && !mine) {
            Spacer(Modifier.height(7.dp))
            FilledTonalButton(
                onClick = {
                    onOpenSupport(route, m.handoffSubject.orEmpty(), m.handoffDetails.orEmpty())
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = c.accentBg, contentColor = c.accent,
                ),
            ) {
                // The server sends an English buttonLabel. Preferring it made the
                // main CTA permanently English in all six locales, so the local
                // translated string wins and the server value is ignored.
                Text(
                    stringResource(R.string.assistant_open_support),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ToolChip(tools: List<String>) {
    val c = LocalAppColors.current
    val label = when (tools.firstOrNull()) {
        "get_attendance_summary" -> stringResource(R.string.assistant_tool_attendance)
        "get_homework" -> stringResource(R.string.assistant_tool_homework)
        "get_fee_status" -> stringResource(R.string.assistant_tool_fees)
        "get_timetable" -> stringResource(R.string.assistant_tool_timetable)
        "get_exam_results" -> stringResource(R.string.assistant_tool_results)
        else -> stringResource(R.string.assistant_tool_generic)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.accentBg)
            .border(1.dp, c.glassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Icon(Icons.Filled.Check, null, tint = c.textTertiary, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.5.sp, color = c.textTertiary)
    }
}

/** India's national mental-health helpline. Free, 24/7. */
private const val TELE_MANAS_NUMBER = "14416"

@Composable
private fun ThinkingBubble() {
    val c = LocalAppColors.current
    val announce = stringResource(R.string.assistant_thinking)
    Surface(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite      // TalkBack announces the wait
            contentDescription = announce
        },
        color = c.chatReceived,
        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun SuggestionChips(onPick: (String) -> Unit) {
    val c = LocalAppColors.current
    val chips = listOf(
        stringResource(R.string.assistant_chip_attendance),
        stringResource(R.string.assistant_chip_homework),
        stringResource(R.string.assistant_chip_fees),
        stringResource(R.string.assistant_chip_timetable),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        chips.forEach { label ->
            Surface(
                color = c.glass,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, c.glassBorder),
                onClick = { onPick(label) },
            ) {
                Text(
                    label,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)      // was ~28dp
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    fontSize = 12.2.sp, color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.bgStart)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(stringResource(R.string.assistant_input_hint), fontSize = 13.5.sp)
            },
            maxLines = 4,          // grows a little, never takes the screen
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (enabled) onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent,
                unfocusedBorderColor = c.glassBorder,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
            ),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = c.chatSent, contentColor = c.onBanner,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.assistant_send),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun UnavailableNotice(reason: String) {
    val c = LocalAppColors.current
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())   // text is server-supplied and unbounded
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(reason, fontSize = 14.sp, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}
