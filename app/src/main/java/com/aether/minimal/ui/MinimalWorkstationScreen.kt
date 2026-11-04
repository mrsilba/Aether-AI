package com.aether.minimal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aether.minimal.theme.LocalMinimalColors
import com.aether.minimal.theme.MinimalAetherTheme
import com.aether.minimal.theme.MinimalDimens
import com.aether.minimal.theme.MinimalTypography
import com.aether.ui.WorkstationScreen
import com.aether.ui.WorkstationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalWorkstationScreen(
    viewModel: WorkstationViewModel,
    modifier: Modifier = Modifier
) {
    var isDarkMode by remember { mutableStateOf(true) }

    MinimalAetherTheme(darkTheme = isDarkMode) {
        val colors = LocalMinimalColors.current
        val uiState by viewModel.uiState.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        var showMoreDrawer by remember { mutableStateOf(false) }
        var showCommandPalette by remember { mutableStateOf(false) }

        // BackHandler to navigate back to Home if in sub-screen
        BackHandler(enabled = uiState.currentScreen != WorkstationScreen.DASHBOARD) {
            viewModel.switchScreen(WorkstationScreen.DASHBOARD)
        }

        // Show status message if present
        LaunchedEffect(uiState.statusMessage) {
            uiState.statusMessage?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                viewModel.clearStatusMessage()
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                MinimalHeader(
                    projectName = "Quantum-Dev",
                    modelName = uiState.activeLoadedModel?.name ?: "TinyCoder 1.5B",
                    deviceRamInfo = uiState.hardwareProfile?.let {
                        "${it.availableRamMb}MB free • ${it.effectiveTier.name}"
                    } ?: "Ready",
                    onSearchClick = { showCommandPalette = true },
                    onModelClick = { viewModel.switchScreen(WorkstationScreen.MODELS) }
                )
            },
            bottomBar = {
                MinimalBottomNavigation(
                    currentScreen = uiState.currentScreen,
                    onNavigate = { screen ->
                        viewModel.switchScreen(screen)
                    },
                    onOpenMoreMenu = { showMoreDrawer = true }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Route to current screen
                when (uiState.currentScreen) {
                    WorkstationScreen.DASHBOARD -> {
                        MinimalHomeScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigate = { viewModel.switchScreen(it) }
                        )
                    }
                    WorkstationScreen.EDITOR -> {
                        MinimalEditorScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    WorkstationScreen.TERMINAL -> {
                        MinimalTerminalScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    WorkstationScreen.AGENT -> {
                        MinimalAgentScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    WorkstationScreen.AUDIT -> {
                        MinimalAuditScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onReviewPatch = { finding ->
                                viewModel.reviewFindingPatch(finding)
                            }
                        )
                    }
                    WorkstationScreen.MODELS -> {
                        MinimalModelsScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                    WorkstationScreen.MEMORY -> {
                        MinimalWorkspaceScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigate = { viewModel.switchScreen(it) }
                        )
                    }
                    WorkstationScreen.SETTINGS -> {
                        MinimalSettingsScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { isDarkMode = it }
                        )
                    }
                }
            }
        }

        // More Destinations Drawer / Bottom Sheet
        if (showMoreDrawer) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showMoreDrawer = false },
                sheetState = sheetState,
                containerColor = colors.surface,
                contentColor = colors.textPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MinimalDimens.padStandard, vertical = MinimalDimens.padSm)
                ) {
                    Text(
                        text = "Workstation Tools & Hubs",
                        style = MinimalTypography.headline.copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(MinimalDimens.padSm))

                    val drawerItems = listOf(
                        Triple(WorkstationScreen.MODELS, "Models Hub", Icons.Default.Psychology),
                        Triple(WorkstationScreen.AUDIT, "Security & Audit", Icons.Default.Security),
                        Triple(WorkstationScreen.MEMORY, "Workspace & Memory", Icons.Default.Folder),
                        Triple(WorkstationScreen.SETTINGS, "Settings & Diagnostics", Icons.Default.Settings)
                    )

                    drawerItems.forEach { (screen, title, icon) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                .clickable {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        showMoreDrawer = false
                                        viewModel.switchScreen(screen)
                                    }
                                }
                                .padding(vertical = MinimalDimens.padMd, horizontal = MinimalDimens.padSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = title, tint = colors.accent, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(MinimalDimens.padStandard))
                            Text(title, style = MinimalTypography.body.copy(color = colors.textPrimary))
                        }
                    }

                    Spacer(modifier = Modifier.height(MinimalDimens.padLg))
                }
            }
        }

        // Fast Command Palette Dialog (⌘K)
        if (showCommandPalette) {
            Dialog(onDismissRequest = { showCommandPalette = false }) {
                var query by remember { mutableStateOf("") }

                val commands = listOf(
                    "Switch to Code Editor" to { viewModel.switchScreen(WorkstationScreen.EDITOR) },
                    "Switch to Sandboxed Terminal" to { viewModel.switchScreen(WorkstationScreen.TERMINAL) },
                    "Launch Autonomous ReAct Agent" to { viewModel.switchScreen(WorkstationScreen.AGENT) },
                    "Run Full Security Audit" to {
                        viewModel.runProjectAudit()
                        viewModel.switchScreen(WorkstationScreen.AUDIT)
                    },
                    "View Local GGUF Models" to { viewModel.switchScreen(WorkstationScreen.MODELS) },
                    "Run Robolectric Unit Tests" to {
                        viewModel.runTerminalCommand("gradle :app:testDebugUnitTest")
                        viewModel.switchScreen(WorkstationScreen.TERMINAL)
                    },
                    "Check Git Status" to {
                        viewModel.runTerminalCommand("git status")
                        viewModel.switchScreen(WorkstationScreen.TERMINAL)
                    },
                    "Toggle Theme" to { isDarkMode = !isDarkMode }
                )

                val filtered = commands.filter { it.first.contains(query, ignoreCase = true) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MinimalDimens.radiusLg))
                        .background(colors.surfaceElevated)
                        .border(MinimalDimens.hairline, colors.borderHairline, RoundedCornerShape(MinimalDimens.radiusLg))
                        .padding(MinimalDimens.padStandard)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(MinimalDimens.padSm))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = MinimalTypography.body.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("command_palette_input"),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text("Search commands, tools, or screens...", style = MinimalTypography.body.copy(color = colors.textTertiary))
                                    }
                                    inner()
                                }
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textTertiary,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { showCommandPalette = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(MinimalDimens.padStandard))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        items(filtered) { (label, action) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(MinimalDimens.radiusSm))
                                    .clickable {
                                        showCommandPalette = false
                                        action()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MinimalTypography.subhead.copy(color = colors.textPrimary))
                                Text("↵", style = MinimalTypography.caption.copy(color = colors.textTertiary))
                            }
                        }
                    }
                }
            }
        }
    }
}
