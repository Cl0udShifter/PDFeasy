package com.cl0udshifter.pdfeasy.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cl0udshifter.pdfeasy.R

private object SettingsScreenDimens {
    val CompactBreakpoint = 600.dp
    val CompactHorizontalMargin = 18.dp
    val ExpandedHorizontalMargin = 24.dp
    val MaxContentWidth = 560.dp
    val SectionGap = 20.dp
    val HeaderToCardGap = 8.dp
    val BottomPadding = 32.dp
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ObserveSettingsLifecycleAndEvents(
        viewModel = viewModel,
        statusMessageRes = uiState.statusMessageRes,
        snackbarHostState = snackbarHostState,
        context = context,
    )

    val callbacks =
        remember(viewModel, onNavigateBack, context) {
            SettingsCallbacks(
                onBackClick = onNavigateBack,
                onAppThemeSelected = viewModel::setAppTheme,
                onDefaultRenderThemeSelected = viewModel::setDefaultRenderTheme,
                onDefaultScrollModeSelected = viewModel::setDefaultScrollMode,
                onDefaultZoomChanged = viewModel::setDefaultZoomScale,
                onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                onIncludePreReleasesChanged = viewModel::setIncludePreReleases,
                onCheckUpdatesOnStartupChanged = viewModel::setCheckUpdatesOnStartup,
                onCheckUpdatesClick = { viewModel.checkForUpdates(silentOnFailure = false) },
                onToggleReleaseNotes = viewModel::toggleReleaseNotes,
                onDownloadUpdateClick = viewModel::downloadUpdate,
                onInstallReadyClick = viewModel::installReadyApk,
                onRequestInstallPermissionClick = { openUnknownSourcesSettings(context) },
                onClearUpdateCacheClick = viewModel::clearUpdateCache,
            )
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        SettingsScreenContent(
            uiState = uiState,
            callbacks = callbacks,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val horizontalMargin =
            if (maxWidth >= SettingsScreenDimens.CompactBreakpoint) {
                SettingsScreenDimens.ExpandedHorizontalMargin
            } else {
                SettingsScreenDimens.CompactHorizontalMargin
            }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = SettingsScreenDimens.MaxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalMargin)
                    .padding(bottom = SettingsScreenDimens.BottomPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsScreenDimens.SectionGap),
        ) {
            SettingsTopBar(onBackClick = callbacks.onBackClick)
            AppearanceSection(uiState = uiState, callbacks = callbacks)
            ReaderDefaultsSection(uiState = uiState, callbacks = callbacks)
            Column(verticalArrangement = Arrangement.spacedBy(SettingsScreenDimens.HeaderToCardGap)) {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_about_updates))
                AboutAndUpdatesCard(uiState = uiState, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsScreenDimens.HeaderToCardGap)) {
        SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
        SettingsCardContainer {
            SettingsSegmentedBlock(
                title = stringResource(R.string.settings_app_theme_label),
                subtitle = stringResource(R.string.settings_app_theme_desc),
                options = rememberAppThemeOptions(),
                selectedValue = uiState.settings.appTheme,
                onSelect = callbacks.onAppThemeSelected,
            )
        }
    }
}

@Composable
private fun ReaderDefaultsSection(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsScreenDimens.HeaderToCardGap)) {
        SettingsSectionHeader(
            title = stringResource(R.string.settings_section_reader_defaults),
            subtitle = stringResource(R.string.settings_defaults_hint),
        )
        SettingsCardContainer {
            SettingsSegmentedBlock(
                title = stringResource(R.string.settings_default_render_theme_label),
                options = rememberRenderThemeOptions(),
                selectedValue = uiState.settings.defaultRenderTheme,
                onSelect = callbacks.onDefaultRenderThemeSelected,
            )
            SettingsDivider()
            SettingsSegmentedBlock(
                title = stringResource(R.string.settings_default_scroll_mode_label),
                options = rememberScrollModeOptions(),
                selectedValue = uiState.settings.defaultScrollMode,
                onSelect = callbacks.onDefaultScrollModeSelected,
            )
            SettingsDivider()
            SettingsZoomRow(
                zoomScale = uiState.settings.defaultZoomScale,
                onZoomChange = callbacks.onDefaultZoomChanged,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_keep_screen_on_label),
                description = stringResource(R.string.settings_keep_screen_on_desc),
                checked = uiState.settings.keepScreenOn,
                onCheckedChange = callbacks.onKeepScreenOnChanged,
                switchTestTag = "settings_keep_screen_on_switch",
            )
        }
    }
}

@Composable
private fun ObserveSettingsLifecycleAndEvents(
    viewModel: SettingsViewModel,
    statusMessageRes: Int?,
    snackbarHostState: SnackbarHostState,
    context: Context,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshInstallPermission()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.LaunchPackageInstaller -> {
                    val launched = launchPackageInstaller(context, event.contentUriString)
                    if (!launched) {
                        viewModel.reportInstallerUnavailable()
                    }
                }
            }
        }
    }

    val statusText = statusMessageRes?.let { stringResource(it) }
    LaunchedEffect(statusMessageRes, statusText) {
        if (statusMessageRes != null && statusText != null) {
            snackbarHostState.showSnackbar(statusText)
            viewModel.dismissStatusMessage()
        }
    }
}

private fun launchPackageInstaller(
    context: Context,
    contentUriString: String,
): Boolean {
    val installIntent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(contentUriString), SettingsScreenDimens.APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return try {
        context.startActivity(installIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun openUnknownSourcesSettings(context: Context) {
    val settingsIntent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        context.startActivity(settingsIntent)
    } catch (_: ActivityNotFoundException) {
        // Dispositivo sem tela dedicada de fontes desconhecidas
    }
}
