package com.alex.aceshare.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.alex.aceshare.R
import com.alex.aceshare.data.update.AppUpdateInfo
import com.alex.aceshare.data.update.ReleaseNoteAudience
import com.alex.aceshare.data.update.ReleaseNoteBlock
import com.alex.aceshare.data.update.parseReleaseNotes
import com.alex.aceshare.ui.components.FocusableButton
import com.alex.aceshare.ui.theme.TvBackground
import com.alex.aceshare.ui.theme.TvBorder
import com.alex.aceshare.ui.theme.TvBorderFocused
import com.alex.aceshare.ui.theme.TvPrimary
import com.alex.aceshare.ui.theme.TvSurface
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun UpdateAvailableDialog(
    info: AppUpdateInfo,
    currentVersionName: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blocks = remember(info.releaseNotes, info.versionName) {
        parseReleaseNotes(
            info.releaseNotes,
            displayedVersion = info.versionName,
            audience = ReleaseNoteAudience.Tv,
        )
    }
    val versionDescription = stringResource(
        R.string.update_message,
        currentVersionName,
        info.versionName,
    )
    val configuration = LocalConfiguration.current
    val cardWidth = (configuration.screenWidthDp * 0.7f).dp.coerceAtMost(920.dp)
    val cardMaxHeight = (configuration.screenHeightDp * 0.8f).dp
    val updateButton = remember { FocusRequester() }
    var buttonReady by remember { mutableStateOf(false) }

    LaunchedEffect(buttonReady) {
        if (buttonReady) updateButton.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(cardWidth)
                .heightIn(max = cardMaxHeight)
                .background(TvSurface, RoundedCornerShape(20.dp))
                .border(1.dp, TvBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 28.dp, vertical = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.update_title),
                color = TvTextPrimary,
                fontSize = 32.sp,
            )
            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clearAndSetSemantics { contentDescription = versionDescription },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentVersionName,
                    color = TvTextSecondary,
                    fontSize = 20.sp,
                )
                Text(
                    text = "→",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = TvTextSecondary,
                    fontSize = 20.sp,
                )
                Text(
                    text = info.versionName,
                    modifier = Modifier
                        .background(TvPrimary.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = TvPrimary,
                    fontSize = 20.sp,
                )
            }
            if (blocks.isEmpty()) {
                Text(
                    text = stringResource(R.string.update_notes_empty),
                    modifier = Modifier.padding(top = 16.dp),
                    color = TvTextSecondary,
                    fontSize = 18.sp,
                )
            } else {
                UpdateNotes(
                    blocks = blocks,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row {
                FocusableButton(
                    label = stringResource(R.string.update_now),
                    onClick = onUpdate,
                    rootModifier = Modifier
                        .focusRequester(updateButton)
                        .onGloballyPositioned { buttonReady = true },
                )
                Spacer(Modifier.width(16.dp))
                FocusableButton(
                    label = stringResource(R.string.update_later),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdateNotes(
    blocks: List<ReleaseNoteBlock>,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollJob = remember { mutableStateOf<Job?>(null) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .border(2.dp, if (focused) TvBorderFocused else TvBorder, shape)
            .clip(shape)
            .background(TvBackground)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = (scroll.viewportSize * 0.4f).toInt().coerceAtLeast(80)
                val delta = when (event.key) {
                    Key.DirectionDown -> step
                    Key.DirectionUp -> -step
                    else -> return@onPreviewKeyEvent false
                }
                val next = (scroll.value + delta).coerceIn(0, scroll.maxValue)
                if (next == scroll.value) return@onPreviewKeyEvent false
                scrollJob.value?.cancel()
                scrollJob.value = scope.launch { scroll.scrollTo(next) }
                true
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = scroll.maxValue > 0),
    ) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
        ) {
            blocks.forEachIndexed { index, block ->
                val top = noteTopPadding(index, blocks)
                when (block) {
                    is ReleaseNoteBlock.Heading -> Text(
                        text = block.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = top),
                        color = TvTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 30.sp,
                    )
                    is ReleaseNoteBlock.Bullet -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = top),
                    ) {
                        Box(
                            Modifier
                                .padding(top = 10.dp, end = 12.dp)
                                .size(8.dp)
                                .background(TvPrimary, CircleShape),
                        )
                        Text(
                            text = block.text,
                            modifier = Modifier.weight(1f),
                            color = TvTextPrimary,
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                        )
                    }
                    is ReleaseNoteBlock.Paragraph -> Text(
                        text = block.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = top),
                        color = TvTextSecondary,
                        fontSize = 18.sp,
                        lineHeight = 28.sp,
                    )
                }
            }
        }
    }
}

private fun noteTopPadding(index: Int, blocks: List<ReleaseNoteBlock>): Dp {
    if (index == 0) return 0.dp
    return when {
        blocks[index] is ReleaseNoteBlock.Heading -> 18.dp
        blocks[index - 1] is ReleaseNoteBlock.Heading -> 8.dp
        else -> 10.dp
    }
}
