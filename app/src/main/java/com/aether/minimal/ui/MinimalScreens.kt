package com.aether.minimal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.cloud.CloudProviderType
import com.aether.editor.DiffType
import com.aether.hardware.DeviceHardwareTier
import com.aether.inference.InferenceBackendType
import com.aether.inference.ModelDescriptor
import com.aether.minimal.theme.LocalMinimalColors
import com.aether.minimal.theme.MinimalDimens
import com.aether.minimal.theme.MinimalTypography
import com.aether.model.CompatibilityRating
import com.aether.model.DownloadStatus
import com.aether.model.HubModelEntry
import com.aether.model.ModelSourceProvider
import com.aether.project.AuditFinding
import com.aether.project.FindingSeverity
import com.aether.ui.WorkstationScreen
import com.aether.ui.WorkstationUiState
import com.aether.ui.WorkstationViewModel
import java.io.File

// ==========================================
// 1. MINIMAL HOME SCREEN
// ==========================================

@Composable
fun MinimalHomeScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    onNavigate: (WorkstationScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var composerText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard),
        verticalArrangement = Arrangement.spacedBy(MinimalDimens.padStandard),
        contentPadding = PaddingValues(top = MinimalDimens.padSm, bottom = 80.dp)
    ) {
        // Quiet Greeting & Status
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "AETHER WORKSTATION",
                    style = MinimalTypography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentMuted,
                        letterSpacing = 1.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Good afternoon, Engineer.",
                    style = MinimalTypography.largeTitle.copy(color = colors.textPrimary)
                )
                Spacer(modifier = Modifier.height(4.dp))
                val ramStr = uiState.hardwareProfile?.let {
                    "RAM: ${it.availableRamMb}MB free of ${it.totalRamMb}MB • Tier: ${it.effectiveTier.name}"
                } ?: "System Ready"
                Text(
                    text = ramStr,
                    style = MinimalTypography.footnote.copy(color = colors.textTertiary)
                )
            }
        }

        // Minimal AI-First Composer
        item {
            MinimalContextualComposer(
                promptText = composerText,
                onPromptChange = { composerText = it },
                onSubmit = { prompt ->
                    val lower = prompt.lowercase()
                    when {
                        lower.contains("test") || lower.contains("gradle") || lower.contains("run") -> {
                            viewModel.runTerminalCommand(prompt)
                            onNavigate(WorkstationScreen.TERMINAL)
                        }
                        lower.contains("audit") || lower.contains("security") -> {
                            viewModel.runProjectAudit()
                            onNavigate(WorkstationScreen.AUDIT)
                        }
                        lower.contains("agent") || lower.contains("fix") || lower.contains("solve") -> {
                            viewModel.startReActAgent(prompt)
                            onNavigate(WorkstationScreen.AGENT)
                        }
                        else -> {
                            viewModel.runInference(prompt)
                        }
                    }
                    composerText = ""
                },
                onQuickActionSelect = { action ->
                    composerText = action
                },
                isExecuting = uiState.isInferenceRunning
            )
        }

        // Live Inference Output Stream
        if (uiState.inferenceOutput.isNotEmpty() || uiState.isInferenceRunning) {
            item {
                MinimalCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AETHER INTELLIGENCE",
                                style = MinimalTypography.caption.copy(color = colors.accent, fontWeight = FontWeight.SemiBold)
                            )
                            if (uiState.tokensPerSec > 0) {
                                Text(
                                    text = "${String.format("%.1f", uiState.tokensPerSec)} tok/s",
                                    style = MinimalTypography.caption.copy(color = colors.textTertiary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(MinimalDimens.padSm))
                        Text(
                            text = uiState.inferenceOutput.ifEmpty { "Generating thoughts..." },
                            style = MinimalTypography.codeMedium.copy(color = colors.textPrimary)
                        )
                    }
                }
            }
        }

        // Core Destination Quick-Access Cards
        item {
            MinimalSectionTitle(title = "Workstation Modules", subtitle = "Direct access to developer tooling")
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                // Models Hub Card
                MinimalCard(onClick = { onNavigate(WorkstationScreen.MODELS) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(MinimalDimens.padMd))
                            Column {
                                Text("AI Model Hub & Discovery", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                                Text(
                                    text = "${uiState.hubModels.size} models • HuggingFace & GGUF catalog",
                                    style = MinimalTypography.caption.copy(color = colors.textSecondary)
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
                    }
                }

                // Editor Card
                MinimalCard(onClick = { onNavigate(WorkstationScreen.EDITOR) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(MinimalDimens.padMd))
                            Column {
                                Text("Code Editor", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                                Text(
                                    text = uiState.activeTab?.let { "Active: ${it.fileName}" } ?: "Multi-tab editor ready",
                                    style = MinimalTypography.caption.copy(color = colors.textSecondary)
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
                    }
                }

                // Terminal Card
                MinimalCard(onClick = { onNavigate(WorkstationScreen.TERMINAL) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(MinimalDimens.padMd))
                            Column {
                                Text("Sandboxed Terminal", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                                Text("Linux shell • ProcessBuilder execution", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
                    }
                }

                // Agent Card
                MinimalCard(onClick = { onNavigate(WorkstationScreen.AGENT) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accentSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(MinimalDimens.padMd))
                            Column {
                                Text("ReAct Supervisor Agent", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                                Text(
                                    text = if (uiState.agentState.steps.isNotEmpty()) "Running • ${uiState.agentState.steps.size} steps" else "Idle • Multi-step autonomous reasoning",
                                    style = MinimalTypography.caption.copy(color = colors.textSecondary)
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. MINIMAL EDITOR SCREEN
// ==========================================

@Composable
fun MinimalEditorScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    val activeTab = uiState.activeTab

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(0.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MinimalDimens.padSm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            uiState.openTabs.forEachIndexed { index, tab ->
                val isSelected = tab.id == activeTab?.id
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(if (isSelected) colors.surfaceElevated else Color.Transparent)
                        .border(
                            MinimalDimens.hairline,
                            if (isSelected) colors.borderHairline else Color.Transparent,
                            RoundedCornerShape(MinimalDimens.radiusSm)
                        )
                        .clickable { viewModel.selectEditorTab(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.fileName + (if (tab.isDirty) " •" else ""),
                        style = MinimalTypography.footnote.copy(
                            color = if (isSelected) colors.textPrimary else colors.textTertiary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close tab",
                        tint = colors.textTertiary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { viewModel.closeEditorTab(index) }
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        // Toolbar: Undo, Redo, Save
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(horizontal = MinimalDimens.padStandard, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { viewModel.undoEditor() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { viewModel.redoEditor() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm), verticalAlignment = Alignment.CenterVertically) {
                if (activeTab != null && activeTab.isDirty) {
                    Text("Modified", style = MinimalTypography.caption.copy(color = colors.warning))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.accent)
                        .clickable { viewModel.saveActiveFile() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("editor_save_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", style = MinimalTypography.caption.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }

        // Diff review strip
        if (uiState.pendingDiffLines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.warningSubtle)
                    .border(MinimalDimens.hairline, colors.warning, RoundedCornerShape(0.dp))
                    .padding(MinimalDimens.padStandard)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pending Unified Patch", style = MinimalTypography.headline.copy(color = colors.warning, fontWeight = FontWeight.Bold))
                        Text(uiState.pendingDiffFile ?: "Unknown file", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.surfaceElevated)
                                .clickable { viewModel.rejectPatch() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Reject", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.success)
                                .clickable { viewModel.approveAndApplyPatch() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Apply", style = MinimalTypography.caption.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(MinimalDimens.padSm))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.codeBackground)
                        .padding(MinimalDimens.padSm)
                ) {
                    uiState.pendingDiffLines.take(6).forEach { line ->
                        val (textColor, linePrefix) = when (line.type) {
                            DiffType.ADDITION -> colors.success to "+ "
                            DiffType.DELETION -> colors.error to "- "
                            DiffType.CONTEXT -> colors.textTertiary to "  "
                        }
                        Text(
                            text = linePrefix + line.text,
                            style = MinimalTypography.codeSmall.copy(color = textColor),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Code Editor View with Gutter Line Numbers
        if (activeTab != null) {
            val lines = remember(activeTab.content) { activeTab.content.lines() }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.codeBackground)
            ) {
                Column(
                    modifier = Modifier
                        .background(colors.codeGutter)
                        .padding(horizontal = 8.dp, vertical = MinimalDimens.padSm),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.indices.forEach { index ->
                        Text(
                            text = "${index + 1}",
                            style = MinimalTypography.codeSmall.copy(color = colors.textTertiary),
                            modifier = Modifier.height(18.dp)
                        )
                    }
                }

                BasicTextField(
                    value = activeTab.content,
                    onValueChange = { viewModel.updateEditorContent(it) },
                    textStyle = MinimalTypography.codeSmall.copy(color = colors.textPrimary, lineHeight = 18.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = MinimalDimens.padSm, vertical = MinimalDimens.padSm)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .testTag("code_editor_field")
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No open files", style = MinimalTypography.body.copy(color = colors.textTertiary))
            }
        }
    }
}

// ==========================================
// 3. MINIMAL TERMINAL SCREEN
// ==========================================

@Composable
fun MinimalTerminalScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var commandInput by remember { mutableStateOf("") }
    val terminalScrollState = rememberScrollState()

    LaunchedEffect(uiState.terminalOutput) {
        terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.terminalBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = MinimalDimens.padStandard, vertical = MinimalDimens.padSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isTerminalRunning) colors.warning else colors.success)
                )
                Spacer(modifier = Modifier.width(MinimalDimens.padSm))
                Text(
                    text = if (uiState.isTerminalRunning) "EXECUTING COMMAND..." else "TERMINAL READY",
                    style = MinimalTypography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                    .clickable { viewModel.clearTerminal() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Clear", style = MinimalTypography.caption.copy(color = colors.textTertiary))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(MinimalDimens.padStandard)
                .verticalScroll(terminalScrollState)
        ) {
            Text(
                text = uiState.terminalOutput,
                style = MinimalTypography.codeSmall.copy(color = Color(0xFFE2E8F0)),
                modifier = Modifier.testTag("terminal_output_text")
            )
        }

        val quickCmds = listOf("git status", "ls -la", "echo \$PWD", "gradle --version", "cat AndroidManifest.xml")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated)
                .padding(horizontal = MinimalDimens.padStandard, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)
        ) {
            items(quickCmds) { cmd ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                        .background(colors.surface)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusPill))
                        .clickable {
                            commandInput = cmd
                            viewModel.runTerminalCommand(cmd)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(cmd, style = MinimalTypography.codeSmall.copy(color = colors.textSecondary))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(0.dp))
                .padding(horizontal = MinimalDimens.padStandard, vertical = MinimalDimens.padSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", style = MinimalTypography.codeMedium.copy(color = colors.accent, fontWeight = FontWeight.Bold))

            BasicTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                textStyle = MinimalTypography.codeMedium.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
                decorationBox = { inner ->
                    Box {
                        if (commandInput.isEmpty()) {
                            Text("Type shell command...", style = MinimalTypography.codeMedium.copy(color = colors.textTertiary))
                        }
                        inner()
                    }
                }
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable(
                        enabled = commandInput.isNotBlank() && !uiState.isTerminalRunning,
                        onClick = {
                            viewModel.runTerminalCommand(commandInput)
                            commandInput = ""
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isTerminalRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Run", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ==========================================
// 4. MINIMAL AGENT SCREEN
// ==========================================

@Composable
fun MinimalAgentScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var objectiveInput by remember { mutableStateOf("") }
    val agentState = uiState.agentState

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = MinimalDimens.padStandard)
    ) {
        Spacer(modifier = Modifier.height(MinimalDimens.padSm))

        MinimalCard {
            Column {
                Text("Autonomous ReAct Supervisor", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                Text("Multi-step reasoning, tool execution, and self-correction loop.", style = MinimalTypography.caption.copy(color = colors.textTertiary))

                Spacer(modifier = Modifier.height(MinimalDimens.padSm))

                BasicTextField(
                    value = objectiveInput,
                    onValueChange = { objectiveInput = it },
                    textStyle = MinimalTypography.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.surfaceElevated)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusSm))
                        .padding(MinimalDimens.padSm)
                        .testTag("agent_objective_input"),
                    decorationBox = { inner ->
                        Box {
                            if (objectiveInput.isEmpty()) {
                                Text("Enter autonomous goal (e.g. Audit codebase, check git, or fix issues)...", style = MinimalTypography.body.copy(color = colors.textTertiary))
                            }
                            inner()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(MinimalDimens.padSm))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.accent)
                        .clickable(
                            enabled = objectiveInput.isNotBlank() && !agentState.isRunning,
                            onClick = {
                                viewModel.startReActAgent(objectiveInput)
                            }
                        )
                        .padding(vertical = 10.dp)
                        .testTag("agent_start_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (agentState.isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(MinimalDimens.padSm))
                            Text("Agent In Progress...", style = MinimalTypography.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    } else {
                        Text("Start Autonomous Execution", style = MinimalTypography.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(MinimalDimens.padStandard))

        Text("Execution Trace (${agentState.steps.size} steps)", style = MinimalTypography.headline.copy(color = colors.textPrimary))
        Spacer(modifier = Modifier.height(MinimalDimens.padSm))

        if (agentState.steps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No active execution trace.", style = MinimalTypography.body.copy(color = colors.textTertiary))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(agentState.steps) { step ->
                    MinimalCard {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Step ${step.stepIndex + 1}: ${step.selectedTool}",
                                    style = MinimalTypography.footnote.copy(color = colors.accent, fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Thought: ${step.thought}",
                                style = MinimalTypography.subhead.copy(color = colors.textSecondary)
                            )
                            if (step.observation.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(MinimalDimens.radiusXs))
                                        .background(colors.codeBackground)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = "Observation: ${step.observation}",
                                        style = MinimalTypography.codeSmall.copy(color = colors.textPrimary),
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. MINIMAL WORKSPACE SCREEN (WITH STRUCTURE & IMPORT)
// ==========================================

@Composable
fun MinimalWorkspaceScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    onNavigate: (WorkstationScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var gitRepoUrl by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard),
        verticalArrangement = Arrangement.spacedBy(MinimalDimens.padStandard),
        contentPadding = PaddingValues(top = MinimalDimens.padSm, bottom = 80.dp)
    ) {
        item {
            Text("Active Project Workspace", style = MinimalTypography.largeTitle.copy(color = colors.textPrimary))
            Text("Local directory context, repository state, and project map", style = MinimalTypography.caption.copy(color = colors.textTertiary))
        }

        // Project Structure Analysis Card
        val struct = uiState.projectStructure
        item {
            MinimalCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(struct?.projectType ?: "Generic Workspace", style = MinimalTypography.headline.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                        Text("${struct?.totalFilesCount ?: 0} files", style = MinimalTypography.caption.copy(color = colors.textTertiary))
                    }
                    Text("Primary Language: ${struct?.primaryLanguage ?: "Multi-Language"}", style = MinimalTypography.body.copy(color = colors.textPrimary))
                    Text("Build System: ${struct?.buildSystem ?: "Gradle / Custom"}", style = MinimalTypography.subhead.copy(color = colors.textSecondary))
                    if (!struct?.keyEntryPoints.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Entry Points: ${struct?.keyEntryPoints?.joinToString(", ")}", style = MinimalTypography.codeSmall.copy(color = colors.success))
                    }
                }
            }
        }

        // Import Project Section
        item {
            MinimalSectionTitle(
                title = "Import External Project",
                subtitle = "Clone GitHub repository or extract local ZIP",
                trailingActionText = if (showImportBox) "Hide" else "Open Importer",
                onTrailingActionClick = { showImportBox = !showImportBox }
            )

            AnimatedVisibility(visible = showImportBox) {
                MinimalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Text("GitHub Repository Clone", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                        BasicTextField(
                            value = gitRepoUrl,
                            onValueChange = { gitRepoUrl = it },
                            textStyle = MinimalTypography.codeSmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.surfaceElevated)
                                .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusSm))
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (gitRepoUrl.isEmpty()) {
                                        Text("https://github.com/owner/repository", style = MinimalTypography.codeSmall.copy(color = colors.textTertiary))
                                    }
                                    inner()
                                }
                            }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accent)
                                    .clickable(
                                        enabled = gitRepoUrl.isNotBlank(),
                                        onClick = {
                                            viewModel.cloneGitRepository(gitRepoUrl)
                                            gitRepoUrl = ""
                                            showImportBox = false
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Clone Project", style = MinimalTypography.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }

        // Hardware Specifications
        item {
            MinimalSectionTitle(title = "Hardware Specifications")
            MinimalCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val p = uiState.hardwareProfile
                    Text("Device Tier: ${p?.effectiveTier?.name ?: "STANDARD"}", style = MinimalTypography.body.copy(color = colors.textPrimary))
                    Text("Available RAM: ${p?.availableRamMb ?: 0} MB", style = MinimalTypography.body.copy(color = colors.textSecondary))
                    Text("Total Cores: ${p?.cpuCoreCount ?: 8} cores", style = MinimalTypography.body.copy(color = colors.textSecondary))
                    Text("Thermal Status: ${p?.thermalStatus?.label ?: "NOMINAL"}", style = MinimalTypography.body.copy(color = colors.textSecondary))
                }
            }
        }

        // Quick Operations
        item {
            MinimalSectionTitle(title = "Quick Project Operations")
            Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.surfaceElevated)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusSm))
                        .clickable {
                            viewModel.runTerminalCommand("gradle :app:testDebugUnitTest")
                            onNavigate(WorkstationScreen.TERMINAL)
                        }
                        .padding(MinimalDimens.padStandard)
                ) {
                    Text("Run Robolectric Unit Test Suite", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.surfaceElevated)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusSm))
                        .clickable {
                            viewModel.runProjectAudit()
                            onNavigate(WorkstationScreen.AUDIT)
                        }
                        .padding(MinimalDimens.padStandard)
                ) {
                    Text("Scan for Security Vulnerabilities", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                }
            }
        }
    }
}

// ==========================================
// 6. MINIMAL FILES SCREEN
// ==========================================

@Composable
fun MinimalFilesScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    onNavigate: (WorkstationScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard)
    ) {
        Spacer(modifier = Modifier.height(MinimalDimens.padSm))
        Text("Workspace Files", style = MinimalTypography.largeTitle.copy(color = colors.textPrimary))
        Text("Project directory contents", style = MinimalTypography.caption.copy(color = colors.textTertiary))

        Spacer(modifier = Modifier.height(MinimalDimens.padStandard))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(uiState.openTabs) { tab ->
                MinimalCard(onClick = { onNavigate(WorkstationScreen.EDITOR) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(MinimalDimens.padSm))
                            Column {
                                Text(tab.fileName, style = MinimalTypography.headline.copy(color = colors.textPrimary))
                                Text(tab.filePath, style = MinimalTypography.caption.copy(color = colors.textTertiary))
                            }
                        }
                        Text("${tab.content.length} bytes", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. MINIMAL MODELS HUB & DISCOVERY SCREEN
// ==========================================

@Composable
fun MinimalModelsScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Hub Catalog, 1: Recommendations

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard),
        verticalArrangement = Arrangement.spacedBy(MinimalDimens.padStandard),
        contentPadding = PaddingValues(top = MinimalDimens.padSm, bottom = 80.dp)
    ) {
        item {
            Text("AI Model Hub & Discovery", style = MinimalTypography.largeTitle.copy(color = colors.textPrimary))
            Text("Multi-provider GGUF weights, downloads, & task recommendations", style = MinimalTypography.caption.copy(color = colors.textTertiary))
        }

        // Active Backend Switcher
        item {
            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Inference Mode", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                        Text(
                            text = if (uiState.activeInferenceBackend == InferenceBackendType.LOCAL_CPU_NEON) "Local CPU NEON (Offline GGUF)" else "Cloud Inference Provider",
                            style = MinimalTypography.caption.copy(color = colors.textSecondary)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(if (uiState.activeInferenceBackend == InferenceBackendType.LOCAL_CPU_NEON) colors.accent else colors.surfaceElevated)
                                .clickable { viewModel.switchBackend(InferenceBackendType.LOCAL_CPU_NEON) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Local", style = MinimalTypography.footnote.copy(color = Color.White))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(if (uiState.activeInferenceBackend == InferenceBackendType.CLOUD_GEMINI) colors.accent else colors.surfaceElevated)
                                .clickable { viewModel.switchBackend(InferenceBackendType.CLOUD_GEMINI) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Cloud", style = MinimalTypography.footnote.copy(color = Color.White))
                        }
                    }
                }
            }
        }

        // Tab Selector: Catalog vs Recommendations
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                    .background(colors.surfaceElevated)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(if (selectedTab == 0) colors.surface else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Model Catalog (${uiState.hubModels.size})", style = MinimalTypography.footnote.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(if (selectedTab == 1) colors.surface else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Task Recommendations", style = MinimalTypography.footnote.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary))
                }
            }
        }

        if (selectedTab == 0) {
            // Search Input
            item {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchHubModels(it)
                    },
                    textStyle = MinimalTypography.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                        .background(colors.surface)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusSm))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text("Search models, publishers, or tasks...", style = MinimalTypography.body.copy(color = colors.textTertiary))
                            }
                            inner()
                        }
                    }
                )
            }

            // Models Catalog List
            items(uiState.hubModels) { entry ->
                val model = entry.descriptor
                val isSelected = model.id == uiState.activeLoadedModel?.id
                val compat = viewModel.assessModelCompatibility(model)

                MinimalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(model.name, style = MinimalTypography.headline.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                                            .background(colors.chipBackground)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(entry.provider.badge, style = MinimalTypography.caption.copy(color = colors.textSecondary, fontWeight = FontWeight.SemiBold))
                                    }
                                }
                                Text("${entry.publisher} • ${model.parameters} • ${model.quantization.name}", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                            }

                            // Compatibility Badge
                            if (compat != null) {
                                val badgeColor = when (compat.rating) {
                                    CompatibilityRating.RECOMMENDED -> colors.success
                                    CompatibilityRating.POSSIBLE -> colors.warning
                                    CompatibilityRating.UNSUPPORTED -> colors.error
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(compat.rating.label, style = MinimalTypography.caption.copy(color = badgeColor, fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        Text(entry.highlights, style = MinimalTypography.subhead.copy(color = colors.textSecondary))

                        // Download status & actions
                        when (val status = entry.downloadStatus) {
                            is DownloadStatus.Downloading -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Downloading: ${status.percent}% (${status.bytesDownloaded / 1048576}MB / ${status.totalBytes / 1048576}MB)", style = MinimalTypography.caption.copy(color = colors.accent))
                                        Text("${status.speedKbps} KB/s", style = MinimalTypography.caption.copy(color = colors.textTertiary))
                                    }
                                    LinearProgressIndicator(
                                        progress = { status.percent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = colors.accent,
                                        trackColor = colors.surfaceElevated
                                    )
                                }
                            }
                            is DownloadStatus.Installed -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Installed on Device", style = MinimalTypography.caption.copy(color = colors.success, fontWeight = FontWeight.Bold))
                                    Row(horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                                .background(if (isSelected) colors.success else colors.accent)
                                                .clickable { viewModel.selectModel(model) }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(if (isSelected) "Active" else "Load", style = MinimalTypography.caption.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                                .background(colors.surfaceElevated)
                                                .clickable { viewModel.deleteHubModel(model.id) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${model.sizeMb} MB download", style = MinimalTypography.caption.copy(color = colors.textTertiary))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                            .background(colors.accent)
                                            .clickable { viewModel.downloadHubModel(entry) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Download", style = MinimalTypography.caption.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Task Recommendations Tab
            items(uiState.taskRecommendations) { rec ->
                MinimalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(rec.category.title, style = MinimalTypography.headline.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                                    .background(colors.accentSubtle)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(rec.primaryBadge, style = MinimalTypography.caption.copy(color = colors.accent, fontWeight = FontWeight.Bold))
                            }
                        }
                        Text("Recommended: ${rec.recommendedModelId}", style = MinimalTypography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold))
                        Text(rec.rationale, style = MinimalTypography.subhead.copy(color = colors.textSecondary))
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. MINIMAL GIT REPOSITORY SCREEN
// ==========================================

@Composable
fun MinimalGitScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var commitMessage by remember { mutableStateOf("") }
    var cloneUrl by remember { mutableStateOf("") }
    var cloneToken by remember { mutableStateOf("") }
    var showCloneBox by remember { mutableStateOf(false) }

    val status = uiState.gitStatus

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard),
        verticalArrangement = Arrangement.spacedBy(MinimalDimens.padStandard),
        contentPadding = PaddingValues(top = MinimalDimens.padSm, bottom = 80.dp)
    ) {
        item {
            Text("Source Control (Git)", style = MinimalTypography.largeTitle.copy(color = colors.textPrimary))
            Text("GitHub repository management, clone, & commit actions", style = MinimalTypography.caption.copy(color = colors.textTertiary))
        }

        // Branch & Sync Status Card
        item {
            MinimalCard {
                Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Branch: ${status?.currentBranch ?: "main"}", style = MinimalTypography.headline.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusPill))
                                .background(if (status?.isClean == true) colors.successSubtle else colors.warningSubtle)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (status?.isClean == true) "Clean Tree" else "Modified",
                                style = MinimalTypography.caption.copy(color = if (status?.isClean == true) colors.success else colors.warning, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Text("Remote: ${status?.remoteSyncStatus ?: "Synchronized with remote"}", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                }
            }
        }

        // Clone Remote Repository Card
        item {
            MinimalSectionTitle(
                title = "Clone GitHub Repository",
                subtitle = "Public or private repos with access token",
                trailingActionText = if (showCloneBox) "Hide" else "Clone Repo",
                onTrailingActionClick = { showCloneBox = !showCloneBox }
            )

            AnimatedVisibility(visible = showCloneBox) {
                MinimalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Text("Repository URL", style = MinimalTypography.subhead.copy(color = colors.textSecondary))
                        BasicTextField(
                            value = cloneUrl,
                            onValueChange = { cloneUrl = it },
                            textStyle = MinimalTypography.codeSmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )

                        Text("Personal Access Token (PAT for private repos)", style = MinimalTypography.caption.copy(color = colors.textTertiary))
                        BasicTextField(
                            value = cloneToken,
                            onValueChange = { cloneToken = it },
                            textStyle = MinimalTypography.codeSmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.accent)
                                .clickable(
                                    enabled = cloneUrl.isNotBlank(),
                                    onClick = {
                                        viewModel.cloneGitRepository(cloneUrl, patToken = cloneToken.ifBlank { null })
                                        cloneUrl = ""
                                        cloneToken = ""
                                        showCloneBox = false
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Execute Clone", style = MinimalTypography.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        // Commit Actions
        item {
            MinimalCard {
                Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                    Text("Commit Working Changes", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                    BasicTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        textStyle = MinimalTypography.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (commitMessage.isEmpty()) {
                                    Text("Enter commit message...", style = MinimalTypography.body.copy(color = colors.textTertiary))
                                }
                                inner()
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.surfaceElevated)
                                .clickable { viewModel.refreshGitStatus() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Refresh Status", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.accent)
                                .clickable(
                                    enabled = commitMessage.isNotBlank(),
                                    onClick = {
                                        viewModel.commitGitChanges(commitMessage)
                                        commitMessage = ""
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Stage All & Commit", style = MinimalTypography.caption.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. MINIMAL AUDIT SCREEN
// ==========================================

@Composable
fun MinimalAuditScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    onReviewPatch: (AuditFinding) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    val report = uiState.auditReport

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard),
        verticalArrangement = Arrangement.spacedBy(MinimalDimens.padStandard),
        contentPadding = PaddingValues(top = MinimalDimens.padSm, bottom = 80.dp)
    ) {
        item {
            Text("Security & Code Audit", style = MinimalTypography.largeTitle.copy(color = colors.textPrimary))
            Text("Automated static vulnerability analysis & patch generator", style = MinimalTypography.caption.copy(color = colors.textTertiary))
        }

        item {
            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = report?.let { "${it.healthScore}/100" } ?: "100/100",
                            style = MinimalTypography.largeTitle.copy(color = colors.success, fontSize = 32.sp)
                        )
                        Text("Code Health Score", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                            .background(colors.accent)
                            .clickable(
                                enabled = !uiState.isAuditing,
                                onClick = { viewModel.runProjectAudit() }
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isAuditing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Run Audit", style = MinimalTypography.footnote.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }

        item {
            MinimalSectionTitle(title = "Findings & Recommendations (${report?.findings?.size ?: 0})")
        }

        if (report == null || report.findings.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No security issues detected. Codebase is clean.", style = MinimalTypography.body.copy(color = colors.textTertiary))
                }
            }
        } else {
            items(report.findings) { finding ->
                val severityColor = when (finding.severity) {
                    FindingSeverity.CRITICAL -> colors.error
                    FindingSeverity.HIGH -> colors.warning
                    FindingSeverity.MEDIUM -> colors.accent
                    FindingSeverity.LOW -> colors.textSecondary
                    FindingSeverity.INFO -> colors.textTertiary
                }

                MinimalCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(finding.title, style = MinimalTypography.headline.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold))
                            Text(finding.severity.name, style = MinimalTypography.caption.copy(color = severityColor, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(finding.description, style = MinimalTypography.subhead.copy(color = colors.textSecondary))
                        Spacer(modifier = Modifier.height(MinimalDimens.padSm))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${finding.filePath}:${finding.lineNumber}", style = MinimalTypography.codeSmall.copy(color = colors.textTertiary))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accentSubtle)
                                    .clickable { onReviewPatch(finding) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Review Patch", style = MinimalTypography.footnote.copy(color = colors.accent, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. MINIMAL SETTINGS & VAULT SCREEN
// ==========================================

@Composable
fun MinimalSettingsScreen(
    uiState: WorkstationUiState,
    viewModel: WorkstationViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMinimalColors.current
    var editingProvider by remember { mutableStateOf<CloudProviderType?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MinimalDimens.padStandard),
        verticalArrangement = Arrangement.spacedBy(MinimalDimens.padStandard),
        contentPadding = PaddingValues(top = MinimalDimens.padSm, bottom = 80.dp)
    ) {
        item {
            Text("Settings & Credential Vault", style = MinimalTypography.largeTitle.copy(color = colors.textPrimary))
            Text("Hardware preferences, themes, & Keystore AES-256 cloud credentials", style = MinimalTypography.caption.copy(color = colors.textTertiary))
        }

        // Appearance
        item {
            MinimalSectionTitle(title = "Appearance")
            MinimalCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dark Theme", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                        Text("Quiet obsidian minimal aesthetic", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onToggleDarkMode,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent)
                    )
                }
            }
        }

        // Cloud AI Provider API Keys Vault
        item {
            MinimalSectionTitle(title = "Cloud AI Provider Credentials (AES-256-GCM Vault)")
            Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                CloudProviderType.values().forEach { provider ->
                    val isConfigured = uiState.cloudApiKeysConfigured[provider] == true
                    MinimalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(provider.displayName, style = MinimalTypography.headline.copy(color = colors.textPrimary, fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = if (isConfigured) "Active • Encrypted in Android Keystore" else "Not configured",
                                    style = MinimalTypography.caption.copy(color = if (isConfigured) colors.success else colors.textTertiary)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.surfaceElevated)
                                    .clickable {
                                        editingProvider = provider
                                        apiKeyInput = viewModel.getCloudApiKey(provider) ?: ""
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(if (isConfigured) "Edit Key" else "Set Key", style = MinimalTypography.caption.copy(color = colors.accent, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }

        // Edit API Key Box if active
        if (editingProvider != null) {
            item {
                MinimalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                        Text("Configure API Key: ${editingProvider?.displayName}", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                        BasicTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            textStyle = MinimalTypography.codeSmall.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.accent)
                                    .clickable {
                                        editingProvider?.let { p ->
                                            viewModel.storeCloudApiKey(p, apiKeyInput)
                                        }
                                        editingProvider = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Save Encrypted Key", style = MinimalTypography.caption.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(colors.surfaceElevated)
                                    .clickable { editingProvider = null }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Cancel", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                            }
                        }
                    }
                }
            }
        }

        // Hardware Tier Override
        item {
            MinimalSectionTitle(title = "Hardware Override")
            MinimalCard {
                Column(verticalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)) {
                    Text("Capability Tier Override", style = MinimalTypography.headline.copy(color = colors.textPrimary))
                    Text("Adjust active compute profile threshold.", style = MinimalTypography.caption.copy(color = colors.textSecondary))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MinimalDimens.padSm)
                    ) {
                        listOf(null, DeviceHardwareTier.STANDARD, DeviceHardwareTier.PERFORMANCE, DeviceHardwareTier.ULTRA_PERFORMANCE).forEach { tier ->
                            val isSelected = uiState.hardwareProfile?.effectiveTier == tier || (tier == null && uiState.hardwareProfile?.userOverrideTier == null)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .background(if (isSelected) colors.accentSubtle else colors.surfaceElevated)
                                    .clickable { viewModel.setDeviceTierOverride(tier) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tier?.name ?: "AUTO",
                                    style = MinimalTypography.caption.copy(
                                        color = if (isSelected) colors.accent else colors.textSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Security Diagnostics
        item {
            MinimalSectionTitle(title = "Security & Encryption")
            MinimalCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Android Keystore Status: Active", style = MinimalTypography.body.copy(color = colors.textPrimary))
                    Text("Cipher: AES/GCM/NoPadding 256-bit", style = MinimalTypography.codeSmall.copy(color = colors.success))
                    Text("SSRF Guard: Enabled (Loopback strictly sandboxed)", style = MinimalTypography.caption.copy(color = colors.textSecondary))
                }
            }
        }
    }
}
