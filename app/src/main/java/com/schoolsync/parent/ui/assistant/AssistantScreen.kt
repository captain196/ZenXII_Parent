package com.schoolsync.parent.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MapsUgc
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
import androidx.compose.ui.text.AnnotatedString
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    /** route, drafted subject, drafted details — the assistant prepared these. */
    onOpenSupport: (String, String, String, String) -> Unit,
    vm: AssistantViewModel = hiltViewModel(),
) {
    val c = LocalAppColors.current
    val ui by vm.ui.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Keep the newest turn in view.
    //
    // Keyed on the reveal cursor as well as the message count: the message is
    // added EMPTY and fills in afterwards, so scrolling only on count put a
    // zero-length bubble at the top of the viewport and then let the text grow
    // downwards out of sight. Observed on device — the answer's tail ended up
    // above the fold with an empty thread beneath it.
    //
    // scrollToItem (not animate) while revealing: an animation cannot keep up
    // with a 16ms tick and fights itself.
    val revealTick = ui.messages.lastOrNull()?.revealChars
    val itemCount = ui.messages.size + if (ui.isThinking) 1 else 0

    // "Already at the bottom" — the standard chat test. canScrollForward is false
    // only when the last item is fully in view.
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    suspend fun jumpToLatest(animate: Boolean = true) {
        if (itemCount <= 0) return
        val last = itemCount - 1
        if (animate) listState.animateScrollToItem(last) else listState.scrollToItem(last)

        // scrollToItem lands on the item's TOP edge. One reply can be taller than
        // the viewport — a week's timetable is — and then this is a no-op, because
        // the top of that message is exactly where we already were. Observed on
        // device: the arrow was visible, tapping it moved nothing, and Friday and
        // Saturday stayed below the fold. "Latest" means the END of the newest
        // message, so scroll off whatever still hangs past the viewport.
        //
        // Measured against the item rather than scrolling by a large constant: an
        // arbitrary overshoot drove content up behind the status bar in an earlier
        // revision of this screen. Bounded to 3 passes because a LazyColumn only
        // measures what it has laid out, so one correction can reveal a little more.
        repeat(3) {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.lastOrNull { it.index == last } ?: return@repeat
            // + afterContentPadding: the list keeps padding below the last bubble, so
            // stopping at the bubble's edge leaves the list still scrollable and the
            // arrow still showing after you tapped it. Consume that too — a chat's
            // jump-to-latest should leave nothing behind it.
            val past = (item.offset + item.size + info.afterContentPadding) - info.viewportEndOffset
            if (past <= 0) return@repeat
            if (animate) listState.animateScrollBy(past.toFloat()) else listState.scrollBy(past.toFloat())
        }
    }

    // 1 · New content follows the conversation — but ONLY if the reader is already
    //     at the bottom. Yanking someone away from a message they scrolled up to
    //     read is the single most irritating thing a chat can do; WhatsApp shows a
    //     jump-to-latest chip instead, which is what FabJumpToLatest below is.
    LaunchedEffect(itemCount, ui.isThinking, revealTick) {
        if (itemCount <= 0) return@LaunchedEffect
        // atBottom ONLY. `|| revealTick != null` was here, and since revealTick is
        // non-null for the whole reveal it bypassed the check on every 16ms tick —
        // so a reader who scrolled up to re-read an earlier turn was snapped back
        // down the instant they lifted their finger, for the ~7 seconds a week's
        // timetable takes to reveal. That is precisely what the note above promises
        // not to do. The empty-bubble case it was meant to fix is already covered:
        // we are at the bottom when the bubble is appended, so the reveal follows.
        if (atBottom) jumpToLatest(animate = revealTick == null)
    }

    // 2 · Opening the keyboard returns you to the newest message. On a long thread
    //     the composer would otherwise slide up over whatever you were reading.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (imeVisible) jumpToLatest() }

    // Back closes the keyboard before it leaves the screen. Without this the first
    // Back exited AND discarded the whole conversation, because nothing is
    // persisted — a student dismissing the keyboard lost the thread.
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = imeVisible) { keyboard?.hide() }

    // 3 · Typing means you are composing a reply, so follow the conversation.
    LaunchedEffect(ui.input.isNotEmpty()) { if (ui.input.isNotEmpty()) jumpToLatest() }

    // Plain container, not Scaffold.
    //
    // Two device-verified facts drive this:
    //  1. With the keyboard up the app window is 1724px of a 2460px screen — the
    //     window ALREADY resizes for the IME. So NO imePadding: adding it
    //     subtracted the keyboard twice and left the composer floating.
    //  2. Scaffold shifted its whole content upward in that resized window, so the
    //     top bar went off-screen and message bubbles drew over the status bar.
    //
    // statusBars + navigationBars, applied once, with the window resize handling
    // the keyboard. Same container shape as SupportComposeScreen:77-84.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgMid)
            .statusBarsPadding()
            // With windowSoftInputMode=adjustResize declared explicitly, the window
            // stops above the keyboard but still spans the navigation-bar strip the
            // keyboard covers — so WindowInsets.ime here reports only the REMAINING
            // overlap, not the whole keyboard. This is the same pair the app's own
            // SupportComposeScreen uses, and it only behaves once the soft-input
            // mode is declared rather than left to the OEM.
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bgStart)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = c.textSecondary,
                    )
                }
                // weight: a Row measures unweighted children first, so once the
                // title wrapped — at large font scale, or in Tamil — it took the
                // whole remaining width and squeezed the New chat action to zero.
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        stringResource(R.string.assistant_title),
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = c.textPrimary,
                    )
                    Text(
                        if (ui.isThinking) stringResource(R.string.assistant_thinking)
                        else stringResource(R.string.assistant_subtitle),
                        fontSize = 11.5.sp, color = c.textSecondary,
                    )
                }

                // The student's own erase. Until this existed there was NO way to
                // clear the conversation — they could only back out, and (since the
                // process cache landed) that now correctly keeps it. Shown only when
                // there is something to clear, so an empty screen stays uncluttered.
                if (ui.messages.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = vm::newChat) {
                        Icon(
                            Icons.Filled.MapsUgc,
                            contentDescription = stringResource(R.string.assistant_new_chat),
                            tint = c.textSecondary,
                        )
                    }
                }
            }

            if (ui.unavailableReason != null) {
                UnavailableNotice(ui.unavailableReason!!)
                return@Column
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (ui.messages.isEmpty()) {
                    // Say the thread is gone rather than just showing the welcome
                    // screen again. The content is deliberately not recoverable
                    // (see AssistantSessionCache), so the honest thing is to state
                    // the limit — otherwise this reads as the app forgetting them.
                    if (ui.threadWasCleared) item { ThreadClearedNotice() }
                    item { Intro() }
                }
                itemsIndexed(ui.messages) { i, m ->
                    MessageRow(m, onOpenSupport, isLatest = i == ui.messages.lastIndex)
                }
                if (ui.isThinking) item { ThinkingBubble() }
            }

                // Floats OVER the thread, bottom-end, like WhatsApp — it must not
                // steal a row of layout height that the conversation could use.
                JumpToLatest(
                    visible = !atBottom && itemCount > 0,
                    onClick = { scope.launch { jumpToLatest() } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp),
                )
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

/**
 * Compact down-arrow that floats over the thread when the reader has scrolled
 * away from the newest message — the WhatsApp affordance.
 *
 * A circular icon rather than a text pill: it overlays the conversation instead
 * of consuming a row, and it needs no translation in six locales because the
 * arrow carries the meaning. The label survives as its contentDescription, so
 * TalkBack still announces it in the user's language.
 */
@Composable
private fun JumpToLatest(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            color = c.chatSent,
            shape = CircleShape,
            shadowElevation = 4.dp,
            // 48dp, not 40: 40 was reasoning from what this app happens to use
            // elsewhere rather than from the 48dp minimum touch target.
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.assistant_jump_latest),
                    tint = c.onBanner,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ThreadClearedNotice() {
    val c = LocalAppColors.current
    Text(
        text = stringResource(R.string.assistant_thread_cleared),
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        color = c.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
    )
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
            // textSecondary, not textTertiary: tertiary on this background measures
            // ~2.8:1. The legally-required "you are talking to an AI" line was the
            // hardest text on the screen to read.
            fontSize = 11.sp, color = c.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: AssistantMessage,
    onOpenSupport: (String, String, String, String) -> Unit,
    isLatest: Boolean = false,
) {
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
            val clipboard = LocalClipboardManager.current
            val ctx = LocalContext.current
            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    // assistantPlainText, NOT the rendered AnnotatedString: on screen
                    // the break between list items is a ParagraphStyle boundary, which
                    // is invisible in the plain string — a whole timetable pasted as
                    // one run-on line. Paragraphs for layout, characters for text.
                    .semantics {
                        contentDescription = if (mine) m.text else assistantPlainText(m.text)
                        // Announce the ANSWER, not just the wait. ThinkingBubble was
                        // the only live region on this screen and it is REMOVED when
                        // the reply lands, so a TalkBack user heard "Looking that
                        // up…" and then nothing at all.
                        //
                        // Gated on revealChars == null so it fires once the text has
                        // finished appearing rather than on every 16ms tick, which
                        // would talk over itself ~400 times for a timetable.
                        if (isLatest && !mine && m.revealChars == null) {
                            liveRegion = LiveRegionMode.Polite
                        }
                    }
                    .combinedClickable(
                        // A label, not a bare long-press: TalkBack announced every
                        // bubble as an activatable control with a do-nothing tap,
                        // and exposed copy only as an unlabeled generic gesture.
                        onClickLabel = null,
                        onLongClickLabel = stringResource(R.string.common_copied),
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(
                                if (mine) m.text else assistantPlainText(m.text)
                            ))
                            Toast.makeText(ctx, ctx.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                        },
                    ),
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
            ) {
                Text(
                    // The model writes **bold** and "* " lists. A plain Text drew
                    // those literally — every list arrived full of asterisks.
                    // The student's own text is NOT parsed: we never restyle what
                    // a person typed.
                    // revealChars drives the progressive reveal. Markdown is parsed
                    // on the revealed slice, so a half-emitted "**" shows as
                    // literal characters for a frame rather than flickering bold.
                    if (mine) AnnotatedString(m.text)
                    else renderAssistantMarkdown(m.revealChars?.let { n -> m.text.take(n) } ?: m.text),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    fontSize = 14.2.sp,
                    lineHeight = 20.sp,
                    color = when {
                        m.isError -> c.error
                        // pillText, not onBanner: pure white on chatSent measures
                        // ~3.5:1, under the 4.5:1 body-text bar. MessagesScreen:741
                        // already pairs chatSent with pillText (~5.3:1) — this screen
                        // had simply diverged from the app's own convention.
                        mine -> c.pillText
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
                    onOpenSupport(route, m.handoffSubject.orEmpty(), m.handoffDetails.orEmpty(),
                        m.handoffCategory.orEmpty())
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
    // firstOrNull() was a false provenance claim: the server pushes EVERY tool it
    // ran, so an answer drawn from attendance AND fees announced only "Your
    // attendance was checked" while showing fee figures — understating which of
    // the child's records were read, on exactly the data the prompt flags as
    // parent-visible. Name the source only when there is precisely one.
    val label = when (tools.distinct().singleOrNull()) {
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
        Text(label, fontSize = 10.5.sp, // textSecondary: tertiary on the chip background measures ~2.6:1. This chip
        // tells a parent WHICH of their child's records were read — it should not
        // be the least legible thing on the screen.
        color = c.textSecondary)
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
            // FilledIconButton is 40dp by default (IconButtonTokens.StateLayerSize)
            // and Surface does not apply Material's minimum-touch-target
            // enforcement, so the send button shipped under the 48dp minimum.
            modifier = Modifier.size(48.dp),
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
