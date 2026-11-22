package com.cl0udshifter.pdfeasy.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    debounceMillis: Long = SEARCH_DEBOUNCE_MS,
) : ViewModel() {
    private val searchQueryFlow = MutableStateFlow("")
    private val activeFilterFlow = MutableStateFlow(LibraryFilter.ALL)
    private val selectedSheetDocumentIdFlow = MutableStateFlow<String?>(null)
    private val errorMessageFlow = MutableStateFlow<String?>(null)

    private val eventsMutable = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val events: SharedFlow<LibraryEvent> = eventsMutable.asSharedFlow()

    private val allDocumentsFlow = libraryRepository.observeLibrary("")
    private val searchResultsFlow =
        searchQueryFlow
            .debounce(debounceMillis)
            .distinctUntilChanged()
            .flatMapLatest { query -> libraryRepository.observeLibrary(query) }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            searchResultsFlow,
            allDocumentsFlow,
            searchQueryFlow,
            activeFilterFlow,
            combine(selectedSheetDocumentIdFlow, errorMessageFlow, ::Pair),
        ) { searchResults, allDocuments, query, filter, sheetAndError ->
            val unavailableCount =
                allDocuments.count { it.document.availability != DocumentAvailability.AVAILABLE }
            val effectiveFilter =
                if (filter == LibraryFilter.UNAVAILABLE && unavailableCount == 0) {
                    LibraryFilter.ALL
                } else {
                    filter
                }
            val visibleItems = applyLibraryFilter(searchResults, effectiveFilter)
            val sheetId = sheetAndError.first
            val selectedForSheet =
                if (sheetId == null) null else allDocuments.firstOrNull { it.document.id == sheetId }

            LibraryUiState(
                items = visibleItems,
                totalDocumentCount = allDocuments.size,
                unavailableCount = unavailableCount,
                searchQuery = query,
                activeFilter = effectiveFilter,
                selectedDocumentForSheet = selectedForSheet,
                errorMessage = sheetAndError.second,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LibraryUiState(),
        )

    fun refreshAvailability() {
        viewModelScope.launch {
            libraryRepository.refreshAvailability()
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun onClearSearch() {
        searchQueryFlow.value = ""
    }

    fun onFilterSelected(filter: LibraryFilter) {
        activeFilterFlow.value = filter
    }

    fun onDocumentClicked(item: LibraryItemWithProgress) {
        if (item.document.availability == DocumentAvailability.AVAILABLE) {
            eventsMutable.tryEmit(
                LibraryEvent.OpenReader(
                    documentId = item.document.id,
                    uriString = item.document.uriString,
                    displayName = item.document.displayName,
                ),
            )
        } else {
            selectedSheetDocumentIdFlow.value = item.document.id
        }
    }

    fun onDocumentOptionsClicked(item: LibraryItemWithProgress) {
        selectedSheetDocumentIdFlow.value = item.document.id
    }

    fun dismissDocumentSheet() {
        selectedSheetDocumentIdFlow.value = null
    }

    fun onDocumentPicked(
        uriString: String,
        replacingDocumentId: String? = null,
    ) {
        viewModelScope.launch {
            when (val result = libraryRepository.registerOrUpdateDocument(uriString)) {
                is CoreResult.Success -> {
                    val registered = result.data
                    if (replacingDocumentId != null && replacingDocumentId != registered.id) {
                        libraryRepository.removeFromLibrary(replacingDocumentId)
                    }
                    selectedSheetDocumentIdFlow.value = null
                    eventsMutable.tryEmit(
                        LibraryEvent.OpenReader(
                            documentId = registered.id,
                            uriString = registered.uriString,
                            displayName = registered.displayName,
                        ),
                    )
                }

                is CoreResult.Failure -> {
                    selectedSheetDocumentIdFlow.value = null
                    errorMessageFlow.value = mapLibraryRegisterError(result.error)
                }
            }
        }
    }

    fun removeFromLibrary(documentId: String) {
        viewModelScope.launch {
            libraryRepository.removeFromLibrary(documentId)
            selectedSheetDocumentIdFlow.value = null
        }
    }

    fun dismissError() {
        errorMessageFlow.value = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 150L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val EVENT_BUFFER_CAPACITY = 8

        fun provideFactory(libraryRepository: LibraryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(libraryRepository = libraryRepository) as T
            }
    }
}

private fun applyLibraryFilter(
    items: List<LibraryItemWithProgress>,
    filter: LibraryFilter,
): List<LibraryItemWithProgress> =
    when (filter) {
        LibraryFilter.ALL -> items
        LibraryFilter.READING ->
            items.filter { it.document.lastOpenedAtEpochMillis != null || it.progress != null }
        LibraryFilter.UNAVAILABLE ->
            items.filter { it.document.availability != DocumentAvailability.AVAILABLE }
    }

private fun mapLibraryRegisterError(error: PdfCoreError): String =
    when (error) {
        PdfCoreError.PermissionDenied -> "Permissão de leitura negada pelo sistema."
        PdfCoreError.FileNotFoundOrMoved -> "O arquivo selecionado não está mais acessível."
        is PdfCoreError.CorruptedOrUnsupportedPdf -> "Arquivo inválido ou formato não suportado."
        is PdfCoreError.IoFailure -> "Erro de leitura ao registrar o documento."
        PdfCoreError.PasswordRequired, PdfCoreError.InvalidPassword ->
            "Este documento exige senha para abertura."
    }
