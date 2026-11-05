package com.alex.aceshare.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.alex.aceshare.core.R as CoreR
import com.alex.aceshare.legal.PrivacyPolicy
import com.alex.aceshare.ui.components.FocusableButton
import com.alex.aceshare.ui.theme.TvBackground
import com.alex.aceshare.ui.theme.TvBorderFocused
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollJob = remember { mutableStateOf<Job?>(null) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        Modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(48.dp),
    ) {
        Row {
            FocusableButton(
                label = stringResource(CoreR.string.action_back),
                onClick = onBack,
            )
        }
        Text(
            text = stringResource(CoreR.string.privacy_policy),
            modifier = Modifier.padding(top = 20.dp),
            color = TvTextPrimary,
            fontSize = 32.sp,
        )
        Text(
            text = stringResource(CoreR.string.privacy_policy_updated, PrivacyPolicy.updatedOn),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            color = TvTextSecondary,
            fontSize = 18.sp,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(2.dp, if (focused) TvBorderFocused else Color.Transparent, shape)
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
                .focusable()
                .verticalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            PrivacyPolicy.sections.forEach { section ->
                Text(
                    text = section.heading,
                    modifier = Modifier.padding(top = 16.dp),
                    color = TvTextPrimary,
                    fontSize = 24.sp,
                )
                section.paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        modifier = Modifier.padding(top = 8.dp),
                        color = TvTextSecondary,
                        fontSize = 20.sp,
                    )
                }
            }
        }
    }
}
