package com.cl0udshifter.pdfeasy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cl0udshifter.pdfeasy.AppContainer
import com.cl0udshifter.pdfeasy.PdfEasyApplication
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.ui.library.LibraryScreen
import com.cl0udshifter.pdfeasy.ui.library.LibraryViewModel
import com.cl0udshifter.pdfeasy.ui.reader.ReaderScreen
import com.cl0udshifter.pdfeasy.ui.reader.ReaderViewModel
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyTheme
import kotlinx.coroutines.launch

sealed interface PdfEasyRoute {
    data object Library : PdfEasyRoute

    data class Reader(
        val documentId: String,
        val uriString: String,
        val displayName: String,
    ) : PdfEasyRoute
}

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer
    private var currentRoute by mutableStateOf<PdfEasyRoute>(PdfEasyRoute.Library)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appContainer = (application as PdfEasyApplication).appContainer

        if (savedInstanceState == null) {
            handleIncomingViewIntent(intent)
        } else {
            restoreRouteFromSavedState(savedInstanceState)
        }

        setContent {
            PdfEasyTheme {
                PdfEasyNavHost(
                    route = currentRoute,
                    appContainer = appContainer,
                    onOpenReader = { docId, uri, name ->
                        currentRoute = PdfEasyRoute.Reader(docId, uri, name)
                    },
                    onNavigateBackToLibrary = {
                        currentRoute = PdfEasyRoute.Library
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingViewIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        when (val route = currentRoute) {
            PdfEasyRoute.Library -> outState.remove(KEY_READER_DOC_ID)
            is PdfEasyRoute.Reader -> {
                outState.putString(KEY_READER_DOC_ID, route.documentId)
                outState.putString(KEY_READER_URI, route.uriString)
                outState.putString(KEY_READER_NAME, route.displayName)
            }
        }
    }

    private fun restoreRouteFromSavedState(savedInstanceState: Bundle) {
        val docId = savedInstanceState.getString(KEY_READER_DOC_ID)
        val uri = savedInstanceState.getString(KEY_READER_URI)
        val name = savedInstanceState.getString(KEY_READER_NAME)
        if (!docId.isNullOrBlank() && !uri.isNullOrBlank() && !name.isNullOrBlank()) {
            currentRoute = PdfEasyRoute.Reader(docId, uri, name)
        }
    }

    private fun handleIncomingViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val incomingUri: Uri = intent.data ?: return

        lifecycleScope.launch {
            when (val result = appContainer.libraryRepository.registerOrUpdateDocument(incomingUri.toString())) {
                is CoreResult.Success -> {
                    val doc = result.data
                    currentRoute =
                        PdfEasyRoute.Reader(
                            documentId = doc.id,
                            uriString = doc.uriString,
                            displayName = doc.displayName,
                        )
                }

                is CoreResult.Failure -> {
                    val message =
                        when (result.error) {
                            PdfCoreError.PermissionDenied -> "Permissão de leitura negada para este arquivo."
                            PdfCoreError.FileNotFoundOrMoved -> "Arquivo não encontrado."
                            else -> "Não foi possível abrir o PDF recebido."
                        }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private companion object {
        const val KEY_READER_DOC_ID = "reader_doc_id"
        const val KEY_READER_URI = "reader_uri"
        const val KEY_READER_NAME = "reader_name"
    }
}

@Composable
private fun PdfEasyNavHost(
    route: PdfEasyRoute,
    appContainer: AppContainer,
    onOpenReader: (documentId: String, uriString: String, displayName: String) -> Unit,
    onNavigateBackToLibrary: () -> Unit,
) {
    when (route) {
        PdfEasyRoute.Library -> {
            val libraryViewModel: LibraryViewModel =
                viewModel(
                    factory = LibraryViewModel.provideFactory(appContainer.libraryRepository),
                )
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateToReader = onOpenReader,
            )
        }

        is PdfEasyRoute.Reader -> {
            BackHandler(onBack = onNavigateBackToLibrary)
            val readerViewModel: ReaderViewModel =
                viewModel(
                    key = route.documentId,
                    factory =
                        ReaderViewModel.provideFactory(
                            documentId = route.documentId,
                            uriString = route.uriString,
                            displayName = route.displayName,
                            pdfEngine = appContainer.pdfEngine,
                            libraryRepository = appContainer.libraryRepository,
                            readingProgressRepository = appContainer.readingProgressRepository,
                            bookmarkRepository = appContainer.bookmarkRepository,
                            readerPreferencesStore = appContainer.readerPreferences,
                        ),
                )
            ReaderScreen(
                viewModel = readerViewModel,
                onNavigateBack = onNavigateBackToLibrary,
            )
        }
    }
}
