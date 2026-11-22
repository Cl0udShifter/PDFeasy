package com.cl0udshifter.pdfeasy.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress

private object LibraryScreenDimens {
    val CompactBreakpoint = 600.dp
    val CompactHorizontalMargin = 18.dp
    val ExpandedHorizontalMargin = 24.dp
    val AdaptiveCardMinWidth = 180.dp
    val GridGutter = 12.dp
    val SearchVerticalGap = 10.dp
    val GridBottomPadding = 28.dp
    const val COMPACT_COLUMNS = 2
    const val PDF_MIME_TYPE = "application/pdf"
}

@Immutable
data class LibraryScreenCallbacks(
    val onOpenPdfClick: () -> Unit,
    val onSettingsClick: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit,
    val onClearSearch: () -> Unit,
    val onFilterSelected: (LibraryFilter) -> Unit,
    val onDocumentClick: (LibraryItemWithProgress) -> Unit,
    val onDocumentOptionsClick: (LibraryItemWithProgress) -> Unit,
)

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToReader: (documentId: String, uriString: String, displayName: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    hasUpdateAvailable: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRelinkDocumentId by remember { mutableStateOf<String?>(null) }

    val openDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { pickedUri: Uri? ->
            val relinkId = pendingRelinkDocumentId
            pendingRelinkDocumentId = null
            if (pickedUri != null) {
                viewModel.onDocumentPicked(
                    uriString = pickedUri.toString(),
                    replacingDocumentId = relinkId,
                )
            }
        }

    ObserveLibraryLifecycleAndEvents(
        viewModel = viewModel,
        errorMessage = uiState.errorMessage,
        snackbarHostState = snackbarHostState,
        onNavigateToReader = onNavigateToReader,
    )

    val launchNewPdfPicker = {
        pendingRelinkDocumentId = null
        openDocumentLauncher.launch(arrayOf(LibraryScreenDimens.PDF_MIME_TYPE))
    }

    val callbacks = rememberLibraryCallbacks(viewModel, launchNewPdfPicker, onOpenSettings)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LibraryScreenContent(
            uiState = uiState,
            callbacks = callbacks,
            modifier = Modifier.padding(innerPadding),
            hasUpdateAvailable = hasUpdateAvailable,
        )
    }

    LibraryDocumentSheetHost(
        sheetItem = uiState.selectedDocumentForSheet,
        viewModel = viewModel,
        onRelink = { docId ->
            pendingRelinkDocumentId = docId
            openDocumentLauncher.launch(arrayOf(LibraryScreenDimens.PDF_MIME_TYPE))
        },
    )
}

@Composable
private fun LibraryDocumentSheetHost(
    sheetItem: LibraryItemWithProgress?,
    viewModel: LibraryViewModel,
    onRelink: (String) -> Unit,
) {
    if (sheetItem == null) return
    DocumentActionsSheet(
        item = sheetItem,
        onDismiss = viewModel::dismissDocumentSheet,
        onOpenClick = {
            viewModel.dismissDocumentSheet()
            viewModel.onDocumentClicked(sheetItem)
        },
        onRelinkClick = {
            viewModel.dismissDocumentSheet()
            onRelink(sheetItem.document.id)
        },
        onRemoveClick = { viewModel.removeFromLibrary(sheetItem.document.id) },
    )
}

@Composable
private fun rememberLibraryCallbacks(
    viewModel: LibraryViewModel,
    onOpenPdf: () -> Unit,
    onOpenSettings: () -> Unit,
): LibraryScreenCallbacks =
    remember(viewModel, onOpenPdf, onOpenSettings) {
        LibraryScreenCallbacks(
            onOpenPdfClick = onOpenPdf,
            onSettingsClick = onOpenSettings,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onClearSearch = viewModel::onClearSearch,
            onFilterSelected = viewModel::onFilterSelected,
            onDocumentClick = viewModel::onDocumentClicked,
            onDocumentOptionsClick = viewModel::onDocumentOptionsClicked,
        )
    }

@Composable
private fun ObserveLibraryLifecycleAndEvents(
    viewModel: LibraryViewModel,
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onNavigateToReader: (documentId: String, uriString: String, displayName: String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshAvailability()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.OpenReader ->
                    onNavigateToReader(event.documentId, event.uriString, event.displayName)
            }
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.dismissError()
        }
    }
}

@Composable
fun LibraryScreenContent(
    uiState: LibraryUiState,
    callbacks: LibraryScreenCallbacks,
    modifier: Modifier = Modifier,
    hasUpdateAvailable: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= LibraryScreenDimens.CompactBreakpoint
        val horizontalMargin: Dp =
            if (isExpanded) {
                LibraryScreenDimens.ExpandedHorizontalMargin
            } else {
                LibraryScreenDimens.CompactHorizontalMargin
            }
        val columns =
            if (isExpanded) {
                GridCells.Adaptive(LibraryScreenDimens.AdaptiveCardMinWidth)
            } else {
                GridCells.Fixed(LibraryScreenDimens.COMPACT_COLUMNS)
            }

        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(
                onOpenPdfClick = callbacks.onOpenPdfClick,
                onSettingsClick = callbacks.onSettingsClick,
                hasUpdateAvailable = hasUpdateAvailable,
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin),
            )
            if (uiState.isCompletelyEmpty) {
                LibraryEmptyState(
                    onChoosePdfClick = callbacks.onOpenPdfClick,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LibraryPopulatedBody(
                    uiState = uiState,
                    callbacks = callbacks,
                    horizontalMargin = horizontalMargin,
                    columns = columns,
                )
            }
        }
    }
}

@Composable
private fun LibraryPopulatedBody(
    uiState: LibraryUiState,
    callbacks: LibraryScreenCallbacks,
    horizontalMargin: Dp,
    columns: GridCells,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LibrarySearchBar(
            query = uiState.searchQuery,
            onQueryChange = callbacks.onSearchQueryChange,
            onClearClick = callbacks.onClearSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin),
        )
        Spacer(modifier = Modifier.height(LibraryScreenDimens.SearchVerticalGap))
        LibraryFilterRow(
            totalCount = uiState.totalDocumentCount,
            unavailableCount = uiState.unavailableCount,
            activeFilter = uiState.activeFilter,
            onFilterSelected = callbacks.onFilterSelected,
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalMargin),
        )
        Spacer(modifier = Modifier.height(LibraryScreenDimens.GridGutter))

        if (uiState.items.isEmpty()) {
            LibraryFilteredEmptyState(
                searchQuery = uiState.searchQuery,
                onResetFilters = {
                    callbacks.onClearSearch()
                    callbacks.onFilterSelected(LibraryFilter.ALL)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = columns,
                contentPadding =
                    PaddingValues(
                        start = horizontalMargin,
                        end = horizontalMargin,
                        bottom = LibraryScreenDimens.GridBottomPadding,
                    ),
                horizontalArrangement = Arrangement.spacedBy(LibraryScreenDimens.GridGutter),
                verticalArrangement = Arrangement.spacedBy(LibraryScreenDimens.GridGutter),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = uiState.items,
                    key = { it.document.id },
                ) { item ->
                    LibraryDocumentCard(
                        item = item,
                        onClick = { callbacks.onDocumentClick(item) },
                        onOptionsClick = { callbacks.onDocumentOptionsClick(item) },
                    )
                }
            }
        }
    }
}
