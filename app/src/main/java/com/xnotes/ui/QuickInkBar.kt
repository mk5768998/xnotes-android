package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConversions
import com.xnotes.settings.ToolbarPosition
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.abs
import kotlin.math.pow

/**
 * The quick ink bar: a floating pill under the toolbar while a stroke tool is armed, holding three
 * width favourites and the toolbar's colour swatches (the main bar drops its swatches meanwhile).
 * Tapping a width draws at it; tapping the armed one again opens a slider that retunes it.
 */
@Composable
fun QuickInkBar(editor: Editor, modifier: Modifier = Modifier) {
    val tool = editor.tool
    if (!tool.isStroke) return
    // Always a row, at the toolbar's chosen size, wherever the toolbar itself runs.
    val bar = barMetrics(LocalToolbarLook.current.size)
    val palette = LocalPalette.current
    val shape = MaterialTheme.shapes.extraLarge
    val presets = editor.widthPresetsFor(tool)
    val width = remember(tool, editor.toolStyleVersion) { editor.toolConfig(tool).baseWidth }
    val armed = presets.indexOfFirst { abs(it - width) < WIDTH_MATCH }
    var tuning by remember(tool) { mutableStateOf<Int?>(null) }
    var switcherIndex by remember { mutableStateOf<Int?>(null) }
    CompositionLocalProvider(LocalBar provides bar) {
        Row(
            modifier
                .shadow(6.dp, shape)
                .clip(shape)
                .background(palette.panel.toComposeColor())
                .border(1.dp, palette.border.toComposeColor(), shape)
                .height(bar.thickness)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            presets.forEachIndexed { i, w ->
                Box {
                    WidthChip(tool, w, active = i == armed) {
                        if (i == armed) tuning = i else editor.pickWidth(tool, w)
                    }
                    if (tuning == i) WidthTunePopup(editor, tool, i) { tuning = null }
                }
            }
            Separator()
            editor.toolbarColors.take(editor.toolbarColorCount).forEachIndexed { i, color ->
                Box {
                    Swatch(
                        color = color.toComposeColor(),
                        active = i == editor.activeColorIndex,
                        drag = editor,
                        ink = color,
                        onClick = { if (i == editor.activeColorIndex) switcherIndex = i else editor.pickColor(i) },
                    )
                    if (switcherIndex == i) ColorSwitcherPopup(editor, i) { switcherIndex = null }
                }
            }
        }
    }
}

/** Lays the quick ink bar over the page, just clear of the toolbar on the edge it runs along. */
@Composable
fun BoxScope.QuickInkBarOverlay(editor: Editor) {
    val edge = if (LocalToolbarLook.current.position == ToolbarPosition.BOTTOM) Alignment.BottomCenter else Alignment.TopCenter
    QuickInkBar(editor, Modifier.align(edge).padding(LocalToolbarCover.current).padding(8.dp))
}

/** A width favourite: a short rounded stroke as thick as it draws, relative to the tool's range. */
@Composable
private fun WidthChip(tool: Tool, width: Double, active: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val bar = LocalBar.current
    Box(
        Modifier
            .size(bar.button)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(bar.circle)
                .clip(CircleShape)
                .background(if (active) palette.selectionBackground.toComposeColor() else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (active) palette.selectionForeground else palette.textDim
            Box(
                Modifier
                    .width(bar.icon)
                    .height(chipThickness(tool, width, bar.icon))
                    .clip(RoundedCornerShape(50))
                    .background(ink.toComposeColor()),
            )
        }
    }
}

/** How thick a favourite's stroke shows: 2dp at the thin end of the range up to half the icon. */
private fun chipThickness(tool: Tool, width: Double, icon: Dp): Dp {
    val range = ToolConversions.widthRange(tool)
    val t = ((width - range.start) / (range.endInclusive - range.start)).coerceIn(0.0, 1.0)
    // A root curve, so the everyday thin widths stay told apart instead of all looking hairline.
    return 2.dp + (icon / 2 - 2.dp) * t.pow(0.5).toFloat()
}

/** Retunes favourite [index]: the stroke follows the slider live, the favourite is saved on release. */
@Composable
private fun WidthTunePopup(editor: Editor, tool: Tool, index: Int, onDismiss: () -> Unit) {
    val range = ToolConversions.widthRange(tool)
    var value by remember { mutableStateOf(editor.widthPresetsFor(tool)[index].toFloat()) }
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(250.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
            PopupTitle(stringResource(R.string.width_favourite, index + 1))
            SliderRow(
                stringResource(R.string.caption_width),
                value,
                range.start.toFloat()..range.endInclusive.toFloat(),
                onChangeFinished = { editor.setWidthPreset(tool, index, value.toDouble()) },
            ) {
                value = it
                editor.pickWidth(tool, it.toDouble())
            }
        }
    }
}

/** How near the pen's width must be to a favourite for that favourite to show as armed. */
private const val WIDTH_MATCH = 0.05
