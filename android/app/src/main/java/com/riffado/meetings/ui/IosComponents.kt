package com.riffado.meetings.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Text style with paragraph direction taken from the content (Arabic → RTL, Latin → LTR). */
fun TextStyle.auto(text: String): TextStyle =
    copy(textDirection = TextDirectionDetector.detectDirection(text), textAlign = TextAlign.Start)

// ---------------------------------------------------------------------------------------------
// Navigation chrome
// ---------------------------------------------------------------------------------------------

/** iOS large title, scrolls with content. */
@Composable
fun LargeTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = AppleType.largeTitle.auto(text),
            color = Apple.colors.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

val TopBarHeight = 56.dp

/**
 * Floating top bar. Its glass background and inline title fade in as the large title scrolls away
 * ([collapsed] 0 → 1). Buttons are always-visible glass circles, as in iOS 26.
 */
@Composable
fun GlassTopBar(
    title: String,
    collapsed: Float,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val fraction by animateFloatAsState(collapsed.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMediumLow), label = "topbar")
    Box(modifier.fillMaxWidth()) {
        // Background: blurred glass with a soft fade at its lower edge.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = fraction }
                .glass(RoundedCornerShape(0.dp), GlassStyle.Regular, elevation = 0.dp),
        )
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 12.dp)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
                Text(
                    title,
                    style = AppleType.headline,
                    color = Apple.colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp).graphicsLayer {
                        alpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)
                        translationY = (1f - fraction) * 12f
                    },
                )
                Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------------------------

/** iOS segmented control with a spring-animated sliding thumb. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.fill)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            segment * selected,
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(3.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(if (c.isDark) Color(0xFF636366) else Color.White)
                .specularEdge(RoundedCornerShape(16.dp), 0.6f),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            if (i != selected) {
                                haptics.selection(); onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = AppleType.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp),
                        color = c.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Rounded iOS search field. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearDescription: String? = null,
) {
    val c = Apple.colors
    Row(
        modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.fill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = AppleType.body, color = c.secondaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppleType.body.copy(color = c.label, textDirection = TextDirection.Content),
                cursorBrush = SolidColor(c.tint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                clearDescription,
                tint = c.tertiaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

/** iOS-style toggle (green track, white knob) with a springy knob. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Apple.colors
    val haptics = rememberHaptics()
    val knob by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "knob")
    val track by animateColorAsState(if (checked) c.green else c.fill.copy(alpha = if (c.isDark) 0.5f else 0.35f), label = "track")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(if (checked) track else c.fill.copy(alpha = 0.6f).compositeOver(track))
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Switch) {
                haptics.selection(); onCheckedChange(!checked)
            },
    ) {
        Box(
            Modifier.offset(x = knob, y = 2.dp).size(27.dp)
                .shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White),
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha + background.alpha * (1 - alpha)
    if (a == 0f) return Color.Transparent
    return Color(
        (red * alpha + background.red * background.alpha * (1 - alpha)) / a,
        (green * alpha + background.green * background.alpha * (1 - alpha)) / a,
        (blue * alpha + background.blue * background.alpha * (1 - alpha)) / a,
        a,
    )
}

enum class CapsuleStyle { Filled, Tinted, Glass, Destructive }

/** iOS 26 capsule button. */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CapsuleStyle = CapsuleStyle.Tinted,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Apple.colors.tint,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = CircleShape
    val base = modifier.heightIn(min = 40.dp).pressScale(source, 0.96f).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
    val styled = when (style) {
        CapsuleStyle.Filled -> base.shadow(4.dp, shape, ambientColor = color, spotColor = color).clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color))).specularEdge(shape, 0.7f)
        CapsuleStyle.Tinted -> base.clip(shape).background(color.copy(alpha = 0.15f))
        CapsuleStyle.Destructive -> base.clip(shape).background(Apple.colors.red.copy(alpha = 0.15f))
        CapsuleStyle.Glass -> base.glass(shape, elevation = 4.dp)
    }
    val fg = when (style) {
        CapsuleStyle.Filled -> Color.White
        CapsuleStyle.Destructive -> Apple.colors.red
        CapsuleStyle.Glass -> Apple.colors.label
        CapsuleStyle.Tinted -> color
    }
    Row(
        styled.clickable(source, indication = null, enabled = enabled, role = Role.Button) { haptics.tap(); onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Avatars
// ---------------------------------------------------------------------------------------------

@Composable
fun avatarColor(seed: String): Color {
    val c = Apple.colors
    val palette = listOf(c.tint, c.indigo, c.purple, c.pink, c.orange, c.teal, c.green)
    return palette[(seed.hashCode() and Int.MAX_VALUE) % palette.size]
}

/** Contacts-style avatar: gradient disc with initials, or an icon when there is no name. */
@Composable
fun Avatar(name: String?, isCall: Boolean, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val initials = HomeLogic.initials(name)
    val base = if (initials != null) avatarColor(name.orEmpty()) else if (isCall) Apple.colors.green else Apple.colors.tint
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.75f), base)))
            .specularEdge(CircleShape, 0.7f),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            Text(initials, color = Color.White, style = AppleType.headline.copy(fontSize = (size.value * 0.38f).sp))
        } else {
            Icon(if (isCall) Icons.Filled.Call else Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Grouped inset lists (Settings)
// ---------------------------------------------------------------------------------------------

class InsetGroupScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

val InsetShape = RoundedCornerShape(22.dp)

@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    build: InsetGroupScope.() -> Unit,
) {
    val c = Apple.colors
    val scope = InsetGroupScope().apply(build)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                header,
                style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold).auto(header),
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(Modifier.fillMaxWidth().clip(InsetShape).background(c.cell)) {
            scope.rows.forEachIndexed { i, row ->
                if (i > 0) InsetDivider()
                row()
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = AppleType.footnote.auto(footer),
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun InsetDivider(startIndent: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = startIndent).height(0.5.dp).background(Apple.colors.separator))
}

/** Coloured rounded-square glyph used at the start of settings rows. */
@Composable
fun RowIcon(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
}

@Composable
fun InsetRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Apple.colors.tint,
    value: String? = null,
    valueColor: Color = Apple.colors.secondaryLabel,
    titleColor: Color = Apple.colors.label,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled) { haptics.tap(); onClick() } else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppleType.body.auto(title), color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = AppleType.footnote.auto(subtitle), color = Apple.colors.secondaryLabel)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = AppleType.body, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Apple.colors.tertiaryLabel, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun InsetToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Apple.colors.green,
) {
    InsetRow(title = title, subtitle = subtitle, icon = icon, iconColor = iconColor, trailing = { IosSwitch(checked, onCheckedChange) })
}

/** "Label ........ value" text field row; the value is edited in place, end-aligned like iOS. */
@Composable
fun InsetTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    stacked: Boolean = false,
) {
    val c = Apple.colors
    val field: @Composable (Modifier, TextAlign) -> Unit = { mod, align ->
        Box(mod) {
            if (value.isEmpty()) Text(placeholder, style = AppleType.body.copy(textAlign = align), color = c.tertiaryLabel, maxLines = 1, modifier = Modifier.fillMaxWidth())
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
                textStyle = AppleType.body.copy(color = if (stacked) c.label else c.secondaryLabel, textAlign = align, textDirection = TextDirection.Ltr),
                cursorBrush = SolidColor(c.tint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = AppleType.footnote, color = c.secondaryLabel)
            Spacer(Modifier.height(2.dp))
            field(Modifier.fillMaxWidth(), TextAlign.Start)
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = AppleType.body, color = c.label, modifier = Modifier.padding(end = 12.dp))
            field(Modifier.weight(1f), TextAlign.End)
        }
    }
}

/** Plain tinted action row (e.g. "Delete voice profile"). */
@Composable
fun InsetButtonRow(
    text: String,
    onClick: () -> Unit,
    color: Color = Apple.colors.tint,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    centered: Boolean = false,
) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .clickable(enabled = enabled) { haptics.tap(); onClick() }
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = AppleType.body.auto(text), color = color)
    }
}

/** Free-form content inside an inset group row. */
@Composable
fun InsetContent(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Small status capsule ("OK", "Missing", "Active"). */
@Composable
fun StatusPill(text: String, color: Color) {
    Text(
        text,
        style = AppleType.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
