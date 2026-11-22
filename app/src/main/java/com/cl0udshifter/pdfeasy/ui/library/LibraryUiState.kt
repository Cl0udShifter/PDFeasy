package com.cl0udshifter.pdfeasy.ui.library

import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress

enum class LibraryFilter {
    ALL,
    READING,
    UNAVAILABLE,
}

data class LibraryUiState(
    val items: List<LibraryItemWithProgress> = emptyList(),
    val totalDocumentCount: Int = 0,
    val unavailableCount: Int = 0,
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val selectedDocumentForSheet: LibraryItemWithProgress? = null,
    val errorMessage: String? = null,
) {
    val isCompletelyEmpty: Boolean
        get() = totalDocumentCount == 0 && searchQuery.isBlank()
}

sealed interface LibraryEvent {
    data class OpenReader(
        val documentId: String,
        val uriString: String,
        val displayName: String,
    ) : LibraryEvent
}
