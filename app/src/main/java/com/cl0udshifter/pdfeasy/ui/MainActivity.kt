package com.cl0udshifter.pdfeasy.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cl0udshifter.pdfeasy.AppContainer
import com.cl0udshifter.pdfeasy.PdfEasyApplication
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.ui.library.LibraryScreen
import com.cl0udshifter.pdfeasy.ui.library.LibraryViewModel
import com.cl0udshifter.pdfeasy.ui.reader.ReaderScreen
import com.cl0udshifter.pdfeasy.ui.reader.ReaderViewModel
import com.cl0udshifter.pdfeasy.ui.settings.SettingsScreen
import com.cl0udshifter.pdfeasy.ui.settings.SettingsViewModel
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyTheme
import kotlinx.coroutines.launch

sealed interface PdfEasyRoute {
    data object Library : PdfEasyRoute

    data class Reader(
        val documentId: String,
        val uriString: String,
        val displayName: String,
    ) : PdfEasyRoute

    data class Settings(
        val returnToReader: Reader? = null,
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
            val settingsViewModel: SettingsViewModel =
                viewModel(
                    factory =
                        SettingsViewModel.provideFactory(
                            settingsRepository = appContainer.settingsRepository,
                            appUpdateRepository = appContainer.appUpdateRepository,
                        ),
                )
            val settingsState by settingsViewModel.uiState.collectAsState()
            val darkTheme =
                when (settingsState.settings.appTheme) {
                    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.DARK -> true
                }

            SideEffect {
                val style =
                    if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            PdfEasyTheme(darkTheme = darkTheme) {
                PdfEasyNavHost(
                    route = currentRoute,
                    appContainer = appContainer,
                    settingsViewModel = settingsViewModel,
                    hasUpdateAvailable = settingsState.hasUpdateAvailable,
                    onNavigate = { nextRoute -> currentRoute = nextRoute },
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
        val kind =
            when (currentRoute) {
                PdfEasyRoute.Library -> ROUTE_LIBRARY
                is PdfEasyRoute.Reader -> ROUTE_READER
                is PdfEasyRoute.Settings -> ROUTE_SETTINGS
            }
        outState.putString(KEY_ROUTE_KIND, kind)
        val reader =
            when (val route = currentRoute) {
                is PdfEasyRoute.Reader -> route
                is PdfEasyRoute.Settings -> route.returnToReader
                PdfEasyRoute.Library -> null
            }
        if (reader != null) {
            outState.putString(KEY_READER_DOC_ID, reader.documentId)
            outState.putString(KEY_READER_URI, reader.uriString)
            outState.putString(KEY_READER_NAME, reader.displayName)
        } else {
            outState.remove(KEY_READER_DOC_ID)
            outState.remove(KEY_READER_URI)
            outState.remove(KEY_READER_NAME)
        }
    }

    private fun restoreRouteFromSavedState(savedInstanceState: Bundle) {
        val docId = savedInstanceState.getString(KEY_READER_DOC_ID)
        val uri = savedInstanceState.getString(KEY_READER_URI)
        val name = savedInstanceState.getString(KEY_READER_NAME)
        val restoredReader =
            if (!docId.isNullOrBlank() && !uri.isNullOrBlank() && !name.isNullOrBlank()) {
                PdfEasyRoute.Reader(docId, uri, name)
            } else {
                null
            }
        currentRoute =
            when (savedInstanceState.getString(KEY_ROUTE_KIND)) {
                ROUTE_SETTINGS -> PdfEasyRoute.Settings(returnToReader = restoredReader)
                ROUTE_READER -> restoredReader ?: PdfEasyRoute.Library
                else -> restoredReader ?: PdfEasyRoute.Library
            }
    }

    private fun handleIncomingViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val incomingUri: Uri = intent.data ?: return

        lifecycleScope.launch {
            when (val result = appContainer.libraryRepository.registerOrUpdateDocument(incomingUri.toString())) {
                is CoreResult.Success -> {
                    val doc = result.data
                    currentRoute = PdfEasyRoute.Reader(doc.id, doc.uriString, doc.displayName)
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
        const val KEY_ROUTE_KIND = "route_kind"
        const val ROUTE_LIBRARY = "library"
        const val ROUTE_READER = "reader"
        const val ROUTE_SETTINGS = "settings"
        const val KEY_READER_DOC_ID = "reader_doc_id"
        const val KEY_READER_URI = "reader_uri"
        const val KEY_READER_NAME = "reader_name"
    }
}

@Composable
private fun PdfEasyNavHost(
    route: PdfEasyRoute,
    appContainer: AppContainer,
    settingsViewModel: SettingsViewModel,
    hasUpdateAvailable: Boolean,
    onNavigate: (PdfEasyRoute) -> Unit,
) {
    when (route) {
        PdfEasyRoute.Library -> {
            val libraryViewModel: LibraryViewModel =
                viewModel(
                    factory = LibraryViewModel.provideFactory(appContainer.libraryRepository),
                )
            LibraryScreen(
                viewModel = libraryViewModel,
                onNavigateToReader = { docId, uri, name ->
                    onNavigate(PdfEasyRoute.Reader(docId, uri, name))
                },
                onOpenSettings = { onNavigate(PdfEasyRoute.Settings(returnToReader = null)) },
                hasUpdateAvailable = hasUpdateAvailable,
            )
        }

        is PdfEasyRoute.Reader -> {
            BackHandler { onNavigate(PdfEasyRoute.Library) }
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
                            settingsRepository = appContainer.settingsRepository,
                        ),
                )
            ReaderScreen(
                viewModel = readerViewModel,
                onNavigateBack = { onNavigate(PdfEasyRoute.Library) },
                onOpenSettings = { onNavigate(PdfEasyRoute.Settings(returnToReader = route)) },
            )
        }

        is PdfEasyRoute.Settings -> {
            val backDestination = route.returnToReader ?: PdfEasyRoute.Library
            BackHandler { onNavigate(backDestination) }
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { onNavigate(backDestination) },
            )
        }
    }
}
