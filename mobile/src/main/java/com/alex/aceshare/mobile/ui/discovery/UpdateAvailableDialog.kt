package com.alex.aceshare.mobile.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alex.aceshare.data.update.ReleaseNoteBlock
import com.alex.aceshare.data.update.parseReleaseNotes
import com.alex.aceshare.mobile.R

@Composable
internal fun UpdateAvailableDialog(
    currentVersionName: String,
    versionName: String,
    releaseNotes: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blocks = remember(releaseNotes, versionName) {
        parseReleaseNotes(releaseNotes, displayedVersion = versionName)
    }
    val versionDescription = stringResource(
        R.string.update_message,
        currentVersionName,
        versionName,
    )
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.78f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .heightIn(max = maxDialogHeight)
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clearAndSetSemantics { contentDescription = versionDescription },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentVersionName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = versionName,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (blocks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.update_notes_empty),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ReleaseNotesPanel(
                        blocks = blocks,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_later))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onUpdate) {
                        Text(stringResource(R.string.update_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesPanel(
    blocks: List<ReleaseNoteBlock>,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val notesColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(notesColor)
            .drawWithContent {
                drawContent()
                val max = scroll.maxValue
                if (max <= 0) return@drawWithContent
                val fadeHeight = 28.dp.toPx()
                if (scroll.canScrollBackward) {
                    val topFade = 16.dp.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(notesColor, Color.Transparent),
                            startY = 0f,
                            endY = topFade,
                        ),
                        size = Size(size.width, topFade),
                    )
                }
                if (scroll.canScrollForward) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, notesColor),
                            startY = size.height - fadeHeight,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height - fadeHeight),
                        size = Size(size.width, fadeHeight),
                    )
                }
                val barWidth = 3.dp.toPx()
                val inset = 6.dp.toPx()
                val viewport = scroll.viewportSize.coerceAtLeast(1)
                val trackHeight = (size.height - inset * 2).coerceAtLeast(0f)
                val thumbHeight = (trackHeight * viewport / (viewport + max).toFloat())
                    .coerceIn(24.dp.toPx().coerceAtMost(trackHeight), trackHeight.coerceAtLeast(0f))
                val travel = (trackHeight - thumbHeight).coerceAtLeast(0f)
                val top = inset + travel * (scroll.value / max.toFloat())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(size.width - barWidth - 5.dp.toPx(), top),
                    size = Size(barWidth, thumbHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            },
    ) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(start = 14.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    is ReleaseNoteBlock.Bullet -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = top),
                    ) {
                        Box(
                            Modifier
                                .padding(top = 8.dp, end = 10.dp)
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            text = block.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    is ReleaseNoteBlock.Paragraph -> Text(
                        text = block.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = top),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun noteTopPadding(index: Int, blocks: List<ReleaseNoteBlock>): Dp {
    if (index == 0) return 0.dp
    return when {
        blocks[index] is ReleaseNoteBlock.Heading -> 16.dp
        blocks[index - 1] is ReleaseNoteBlock.Heading -> 6.dp
        else -> 8.dp
    }
}
