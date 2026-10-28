package com.xnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConfig
import com.xnotes.core.tools.ToolConversions
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

/** The pen types the toolbar's single pen button gathers, in the order the panel offers them. */
internal val PEN_TYPES = listOf(Tool.PEN, Tool.DASHED, Tool.CALLIGRAPHY, Tool.SPEED, Tool.TAPER)

/**
 * One toolbar button for every pen type: it shows and arms the type last drawn with, and tapped
 * again while armed opens the [PenPanel], where the type is chosen.
 */
@Composable
internal fun PenButton(
    editor: Editor,
    toolIcons: Map<Tool, ImageVector>,
    configForTool: Tool?,
    setConfigForTool: (Tool?) -> Unit,
) {
    val armed = editor.tool in PEN_TYPES
    val tool = if (armed) editor.tool else editor.lastPen
    // The dashed pen in its dotted mode shows dots, as the quick ink bar draws it.
    val dotted = tool == Tool.DASHED && remember(editor.toolStyleVersion) { editor.toolConfig(Tool.DASHED).dotted }
    val icon = (if (dotted) ImageVector.vectorResource(R.drawable.ic_stroke_dotted) else toolIcons[tool]) ?: return
    Box {
        ToolbarIcon(
            icon,
            stringResource(tool.labelRes),
            active = armed,
            glideKey = tool,
            hint = if (armed) stringResource(R.string.tip_tap_for_settings) else null,
            shortcut = "P",
        ) {
            if (armed) setConfigForTool(tool) else editor.selectTool(tool)
        }
        if (configForTool in PEN_TYPES) PenPanel(editor, toolIcons) { setConfigForTool(null) }
    }
}

/**
 * The pen panel, after Notewise's: a preview stroke, the pen type, width with fine steps,
 * pressure, the type's own control, stabilisation and colour, with the rarer switches folded
 * under "More options". Each change applies at once, as in [ToolConfigPopup].
 */
@Composable
internal fun PenPanel(editor: Editor, toolIcons: Map<Tool, ImageVector>, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.width(320.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PopupTitle(stringResource(Tool.PEN.labelRes)) }
                Icon(
                    XnotesIcons.close,
                    contentDescription = stringResource(R.string.close),
                    tint = palette.textDim.toComposeColor(),
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss).padding(4.dp),
                )
            }
            // Switching type re-reads that type's own settings.
            key(editor.tool) { PenSettings(editor, editor.tool.takeIf { it in PEN_TYPES } ?: editor.lastPen, toolIcons) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PenSettings(editor: Editor, tool: Tool, toolIcons: Map<Tool, ImageVector>) {
    val base = remember { editor.toolConfig(tool) }
    var config by remember { mutableStateOf(base) }
    fun emit(c: ToolConfig) {
        config = c
        editor.updateToolConfig(tool, c)
    }
    val ink = (config.colorOverride ?: editor.toolbarColors.getOrNull(editor.activeColorIndex))?.toComposeColor()
        ?: LocalPalette.current.text.toComposeColor()
    val range = ToolConversions.widthRange(tool)

    PenPreview(tool, config, ink)

    StyleCaption(stringResource(R.string.caption_pen_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PEN_TYPES.forEach { t ->
            PenTypeChip(toolIcons[t], stringResource(t.labelRes), selected = t == tool) { editor.selectTool(t) }
        }
    }
    Spacer(Modifier.size(12.dp))

    // WIDTH: half-point steps either side of the slider for the fine tuning a drag can't hit.
    Text(
        "${stringResource(R.string.caption_width)}  ${"%.1f".format(config.baseWidth)}",
        color = LocalPalette.current.text.toComposeColor(),
        fontSize = 12.sp,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(XnotesIcons.minus, stringResource(R.string.thinner)) {
            emit(config.copy(baseWidth = (config.baseWidth - WIDTH_STEP).coerceIn(range)))
        }
        Slider(
            value = config.baseWidth.toFloat(),
            onValueChange = { emit(config.copy(baseWidth = it.toDouble())) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.weight(1f),
        )
        StepButton(XnotesIcons.plus, stringResource(R.string.thicker)) {
            emit(config.copy(baseWidth = (config.baseWidth + WIDTH_STEP).coerceIn(range)))
        }
    }

    if (tool != Tool.DASHED) {
        ToggleRow(stringResource(R.string.caption_pressure), config.pressureEnabled) { emit(config.copy(pressureEnabled = it)) }
        SliderRow(
            stringResource(R.string.caption_sensitivity),
            ToolConversions.minFactorToSensitivity(config.pressureMinFactor).toFloat(),
            0f..100f,
            enabled = config.pressureEnabled,
        ) { emit(config.copy(pressureMinFactor = ToolConversions.sensitivityToMinFactor(it.toDouble()))) }
    }
    when (tool) {
        Tool.CALLIGRAPHY -> SliderRow(
            stringResource(R.string.caption_multiplier),
            ToolConversions.directionStrengthToMultiplier(config.directionStrength).toFloat(),
            1f..5f,
        ) { emit(config.copy(directionStrength = ToolConversions.multiplierToDirectionStrength(it.toDouble()))) }
        Tool.SPEED -> SliderRow(
            stringResource(R.string.caption_speed),
            ToolConversions.strengthToSpeed(config.speedStrength).toFloat(),
            0f..100f,
        ) { emit(config.copy(speedStrength = ToolConversions.speedToStrength(it.toDouble()))) }
        Tool.TAPER -> SliderRow(
            stringResource(R.string.caption_tip_width),
            (config.taperMinFactor * 100).toFloat(),
            0f..100f,
        ) { emit(config.copy(taperMinFactor = it / 100.0)) }
        Tool.DASHED -> {
            ToggleRow(stringResource(R.string.caption_dotted), config.dotted) { emit(config.copy(dotted = it)) }
            // A dot is as long as the line is wide, so only the dashes have a length to set.
            if (!config.dotted) SliderRow(stringResource(R.string.caption_dash), config.dashLength.toFloat(), 2f..40f) { emit(config.copy(dashLength = it.toDouble())) }
            SliderRow(stringResource(R.string.caption_gap), config.dashGap.toFloat(), 2f..40f) { emit(config.copy(dashGap = it.toDouble())) }
        }
        else -> Unit
    }
    SliderRow(
        stringResource(R.string.caption_stabilization),
        config.stabilization.toFloat(),
        0f..ToolConfig.STABILIZATION_MAX.toFloat(),
    ) { emit(config.copy(stabilization = it.roundToInt().toDouble())) }
    Spacer(Modifier.size(8.dp))

    // COLOUR: the toolbar's swatches, plus a colour of this pen's own that ignores them.
    StyleCaption(stringResource(R.string.caption_colour))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        editor.toolbarColors.take(editor.toolbarColorCount).forEachIndexed { i, c ->
            Swatch(c.toComposeColor(), active = config.colorOverride == null && i == editor.activeColorIndex) {
                editor.pickColor(i)
                if (config.colorOverride != null) emit(config.copy(colorOverride = null))
            }
        }
        ColorPickerDot(config.colorOverride, custom = config.colorOverride != null, onPick = { emit(config.copy(colorOverride = it)) }, dismissOnPick = false) { d, p ->
            ColorPickerPopup(initial = config.colorOverride ?: editor.hostToolbarColors.getOrNull(editor.hostActiveColorIndex), recents = editor.hostRecentColors, onDismiss = d, onPick = p)
        }
    }

    var more by remember { mutableStateOf(false) }
    Text(
        (if (more) "▾  " else "▸  ") + stringResource(R.string.caption_more_options),
        color = LocalPalette.current.textDim.toComposeColor(),
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 10.dp),
    )
    if (more) {
        // SCALE off: ink keeps a constant on-screen thickness whatever zoom you draw at.
        ToggleRow(stringResource(R.string.caption_scale), config.scale) { emit(config.copy(scale = it)) }
        // The dashed pen draws a line, not a fillable ribbon, so a halo has nothing to hug.
        if (tool != Tool.DASHED) {
            ToggleRow(stringResource(R.string.caption_neon), config.neon) { emit(config.copy(neon = it)) }
            if (config.neon) SliderRow(
                stringResource(R.string.caption_glow_intensity),
                ToolConversions.neonStrengthToIntensity(config.neonStrength).toFloat(),
                0f..100f,
            ) { emit(config.copy(neonStrength = ToolConversions.intensityToNeonStrength(it.toDouble()))) }
        }
    }
}

/**
 * A sample S-curve in the pen's width and colour. Only an impression: it draws a plain line
 * (dashed or dotted for the dashed pen), not the engine's pressure, direction or taper shaping.
 */
@Composable
private fun PenPreview(tool: Tool, config: ToolConfig, ink: androidx.compose.ui.graphics.Color) {
    val border = LocalPalette.current.border.toComposeColor()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, border, MaterialTheme.shapes.small),
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.12f, h * 0.70f)
            cubicTo(w * 0.35f, h * 0.05f, w * 0.55f, h * 0.10f, w * 0.62f, h * 0.55f)
            cubicTo(w * 0.68f, h * 0.95f, w * 0.80f, h * 0.70f, w * 0.88f, h * 0.45f)
        }
        val px = (config.baseWidth.toFloat() * density * PREVIEW_SCALE).coerceAtMost(h * 0.4f)
        val dash = if (tool == Tool.DASHED) {
            PathEffect.dashPathEffect(floatArrayOf(config.dashOn.toFloat() * density * PREVIEW_SCALE, config.dashOff().toFloat() * density * PREVIEW_SCALE))
        } else null
        drawPath(path, ink, style = Stroke(width = px, cap = StrokeCap.Round, pathEffect = dash))
        // A faint dot where the preview starts, so a white ink on white paper still shows.
        drawCircle(border, radius = 2f * density, center = Offset(w * 0.12f, h * 0.70f))
    }
}

@Composable
private fun PenTypeChip(icon: ImageVector?, label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) palette.selectionBackground.toComposeColor() else palette.surface.toComposeColor())
            .border(1.dp, if (selected) palette.accent.toComposeColor() else palette.border.toComposeColor(), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) palette.selectionForeground.toComposeColor() else palette.text.toComposeColor()
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = label,
        tint = LocalPalette.current.text.toComposeColor(),
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(6.dp),
    )
}

private const val WIDTH_STEP = 0.5

/** Dp the preview draws per unit of pen width: a touch under one, so the thick end still fits. */
private const val PREVIEW_SCALE = 0.8f
