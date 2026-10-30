package com.alex.aceshare.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.alex.aceshare.ui.theme.TvBorderFocused
import com.alex.aceshare.ui.theme.TvSurface
import com.alex.aceshare.ui.theme.TvSurfaceFocused
import com.alex.aceshare.ui.theme.TvTextPrimary
import com.alex.aceshare.ui.theme.TvTextSecondary

/** Extra space so focused scale + border are not clipped by LazyColumn / parents. */
private val FocusGlowPadding = 8.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    rootModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    Box(modifier = rootModifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FocusGlowPadding),
            shape = ClickableSurfaceDefaults.shape(shape = cardShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvSurface,
                focusedContainerColor = TvSurfaceFocused,
                pressedContainerColor = TvSurfaceFocused,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(3.dp, TvBorderFocused),
                    shape = cardShape,
                ),
            ),
            content = content,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusableButton(
    label: String,
    onClick: () -> Unit,
    rootModifier: Modifier = Modifier,
) {
    val buttonShape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = rootModifier,
        shape = ClickableSurfaceDefaults.shape(shape = buttonShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvSurface,
            focusedContainerColor = TvSurfaceFocused,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, TvBorderFocused),
                shape = buttonShape,
            ),
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            color = TvTextPrimary,
            fontSize = 22.sp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ServerCard(
    name: String,
    address: String,
    version: String?,
    onClick: () -> Unit,
    rootModifier: Modifier = Modifier,
) {
    FocusableCard(onClick = onClick, rootModifier = rootModifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = name,
                color = TvTextPrimary,
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = address,
                color = TvTextSecondary,
                fontSize = 20.sp,
            )
            if (!version.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v${version.trim().removePrefix("v").removePrefix("V")}",
                    color = TvTextSecondary,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LineItemCard(
    title: String,
    preview: String,
    onClick: () -> Unit,
    rootModifier: Modifier = Modifier,
) {
    FocusableCard(onClick = onClick, rootModifier = rootModifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = TvTextSecondary,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (title.isNotBlank()) 8.dp else 0.dp),
                color = TvTextPrimary,
                fontSize = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SectionEmpty(
    message: String,
    rootModifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = rootModifier.padding(32.dp),
        color = TvTextSecondary,
        fontSize = 24.sp,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    rootModifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(8.dp)
    Box(modifier = rootModifier.padding(vertical = FocusGlowPadding)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = chipShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (selected) TvSurfaceFocused else Color.Transparent,
                focusedContainerColor = TvSurfaceFocused,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, TvBorderFocused),
                    shape = chipShape,
                ),
            ),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = TvTextPrimary,
                fontSize = 22.sp,
            )
        }
    }
}
