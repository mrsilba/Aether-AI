package com.aether.minimal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.minimal.theme.LocalMinimalColors
import com.aether.minimal.theme.MinimalDimens
import com.aether.minimal.theme.MinimalTypography
import com.aether.ui.WorkstationScreen

@Composable
fun MinimalHeader(
    projectName: String,
    modelName: String,
    deviceRamInfo: String,
    onSearchClick: () -> Unit,
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = MinimalDimens.padStandard, vertical = MinimalDimens.padSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Minimal brand icon
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                    .background(colors.accentSubtle)
                    .border(MinimalDimens.hairline, colors.borderSubtle, RoundedCornerShape(MinimalDimens.radiusSm)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Æ",
                    style = MinimalTypography.headline.copy(fontWeight = FontWeight.Bold, color = colors.accent)
                )
            }

            Spacer(modifier = Modifier.width(MinimalDimens.padSm))

            Column {
                Text(
                    text = projectName,
                    style = MinimalTypography.headline.copy(color = colors.textPrimary),
                    maxLines = 1
                )
                Text(
                    text = deviceRamInfo,
                    style = MinimalTypography.caption.copy(color = colors.textTertiary),
                    maxLines = 1
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)
        ) {
            // Model selector pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                    .background(colors.surfaceElevated)
                    .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusPill))
                    .clickable(onClick = onModelClick)
                    .padding(horizontal = MinimalDimens.padSm, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.success)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = modelName,
                    style = MinimalTypography.footnote.copy(color = colors.textSecondary),
                    maxLines = 1
                )
            }

            // Command palette search trigger
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(MinimalDimens.hairline, colors.borderHairline, CircleShape)
                    .clickable(onClick = onSearchClick)
                    .testTag("command_search_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Quick Search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MinimalBottomNavigation(
    currentScreen: WorkstationScreen,
    onNavigate: (WorkstationScreen) -> Unit,
    onOpenMoreMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current

    val primaryDestinations = listOf(
        WorkstationScreen.DASHBOARD to ("Home" to Icons.Default.Home),
        WorkstationScreen.EDITOR to ("Editor" to Icons.Default.Code),
        WorkstationScreen.TERMINAL to ("Terminal" to Icons.Default.Terminal),
        WorkstationScreen.AGENT to ("Agent" to Icons.Default.SmartToy)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(
                width = MinimalDimens.hairline,
                color = colors.borderHairline,
                shape = RoundedCornerShape(topStart = MinimalDimens.radiusLg, topEnd = MinimalDimens.radiusLg)
            )
            .padding(horizontal = MinimalDimens.padStandard, vertical = MinimalDimens.padSm),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        primaryDestinations.forEach { (screen, info) ->
            val (label, icon) = info
            val isSelected = currentScreen == screen

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                    .clickable { onNavigate(screen) }
                    .padding(horizontal = MinimalDimens.padSm, vertical = 6.dp)
                    .testTag("nav_${screen.name.lowercase()}")
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) colors.accent else colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    style = MinimalTypography.caption.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.accent else colors.textTertiary
                    )
                )
            }
        }

        // More drawer button for secondary destinations (Models, Audit, Memory, Settings)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                .clickable { onOpenMoreMenu() }
                .padding(horizontal = MinimalDimens.padSm, vertical = 6.dp)
                .testTag("nav_more")
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More features",
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "More",
                style = MinimalTypography.caption.copy(color = colors.textTertiary)
            )
        }
    }
}

@Composable
fun MinimalContextualComposer(
    promptText: String,
    onPromptChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onQuickActionSelect: (String) -> Unit,
    isExecuting: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current

    // Contextual recommendations derived from current text
    val contextualSuggestions = remember(promptText) {
        val lower = promptText.lowercase()
        when {
            lower.contains("test") || lower.contains("run") || lower.contains("build") -> {
                listOf("gradle :app:testDebugUnitTest", "gradle assembleDebug", "git status")
            }
            lower.contains("code") || lower.contains("fun") || lower.contains("class") -> {
                listOf("Extract reusable Composable", "Add unit test for method", "Apply diff patch")
            }
            lower.contains("security") || lower.contains("audit") || lower.contains("key") -> {
                listOf("Scan project for leaked secrets", "Review AES Keystore status", "Check cleartext traffic")
            }
            lower.contains("agent") || lower.contains("fix") || lower.contains("solve") -> {
                listOf("Investigate build failure", "Refactor workspace files", "Execute 9-step ReAct loop")
            }
            else -> {
                listOf("Scan project security", "Run build verification", "Check device capabilities", "Explain active file")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MinimalDimens.radiusLg))
            .background(colors.surfaceElevated)
            .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusLg))
            .padding(MinimalDimens.padStandard)
    ) {
        // Text entry field
        BasicTextField(
            value = promptText,
            onValueChange = onPromptChange,
            textStyle = MinimalTypography.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .testTag("ai_composer_input"),
            decorationBox = { innerTextField ->
                Box {
                    if (promptText.isEmpty()) {
                        Text(
                            text = "Ask AETHER, edit code, or run a terminal task...",
                            style = MinimalTypography.body.copy(color = colors.textTertiary)
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(MinimalDimens.padSm))

        // Contextual suggestion chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm),
            contentPadding = PaddingValues(bottom = MinimalDimens.padSm)
        ) {
            items(contextualSuggestions) { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                        .background(colors.chipBackground)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusPill))
                        .clickable { onQuickActionSelect(suggestion) }
                        .padding(horizontal = MinimalDimens.padMd, vertical = 5.dp)
                ) {
                    Text(
                        text = suggestion,
                        style = MinimalTypography.footnote.copy(color = colors.textSecondary)
                    )
                }
            }
        }

        // Action row: Left accessories, Right submit button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padXs)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.surface)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusSm))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⌥ Context Auto",
                        style = MinimalTypography.caption.copy(color = colors.textSecondary)
                    )
                }
            }

            // Submit Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (promptText.isNotBlank()) colors.accent else colors.surfaceHover)
                    .clickable(
                        enabled = promptText.isNotBlank() && !isExecuting,
                        onClick = { onSubmit(promptText) }
                    )
                    .testTag("ai_composer_submit"),
                contentAlignment = Alignment.Center
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Send",
                        tint = if (promptText.isNotBlank()) Color.White else colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalMinimalColors.current
    val shape = RoundedCornerShape(MinimalDimens.radiusMd)

    val boxModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.surface)
        .border(MinimalDimens.hairline, colors.borderHairline, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(MinimalDimens.padStandard)

    Box(modifier = boxModifier) {
        content()
    }
}

@Composable
fun MinimalSectionTitle(
    title: String,
    subtitle: String? = null,
    trailingActionText: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MinimalDimens.padSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = MinimalTypography.headline.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MinimalTypography.caption.copy(color = colors.textSecondary)
                )
            }
        }

        if (trailingActionText != null && onTrailingActionClick != null) {
            Text(
                text = trailingActionText,
                style = MinimalTypography.footnote.copy(fontWeight = FontWeight.Medium, color = colors.accent),
                modifier = Modifier
                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                    .clickable(onClick = onTrailingActionClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
