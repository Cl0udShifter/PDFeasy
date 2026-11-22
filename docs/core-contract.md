# Core Contract (`com.cl0udshifter.pdfeasy.core`)

Este documento define o contrato formal entre a camada **Core** (`core.model`, `core.pdf`, `core.domain`, `core.data`) e a camada **UI** (`ui`). A camada de UI consome exclusivamente os modelos e interfaces abaixo, sem acoplamento direto com `SQLite`, `ContentResolver`, descritores de arquivo (`ParcelFileDescriptor`) ou instâncias nativas do motor de PDF.

---

## 1. Modelos de Dados (`core.model`)

### 1.1. Documento PDF (`PdfDocumentItem`)
Representa um documento catalogado na biblioteca local (via Storage Access Framework / `ACTION_VIEW`) com seu estado de disponibilidade e metadados.

```kotlin
package com.cl0udshifter.pdfeasy.core.model

data class PdfDocumentItem(
    val id: String,                    // Hash SHA-256 ou UUID determinístico da URI
    val uriString: String,             // URI persistida (content://)
    val displayName: String,           // Nome do arquivo exibido na biblioteca
    val sizeBytes: Long,               // Tamanho do arquivo em bytes
    val pageCount: Int,                // Total de páginas (0 se ainda não aberto ou protegido)
    val isPasswordProtected: Boolean,  // Indica se exige senha para abertura
    val availability: DocumentAvailability, // Estado atual do arquivo no armazenamento
    val addedAtEpochMillis: Long,      // Timestamp de adição à biblioteca
    val lastOpenedAtEpochMillis: Long? // Timestamp da última leitura (null se nunca aberto)
)

enum class DocumentAvailability {
    AVAILABLE,          // Arquivo acessível via URI persistida
    MISSING_OR_MOVED,   // Arquivo deletado ou movido fora do app
    PERMISSION_REVOKED  // Permissão persistente do SAF revogada pelo sistema
}
```

### 1.2. Progresso de Leitura (`ReadingProgress`)
Armazena a posição exata de leitura e o estado da viewport para restauração instantânea a 120FPS.

```kotlin
package com.cl0udshifter.pdfeasy.core.model

data class ReadingProgress(
    val documentId: String,            // FK para PdfDocumentItem.id
    val currentPageIndex: Int,         // Índice da página atual (base 0)
    val totalPages: Int,               // Total de páginas conhecido
    val scrollOffsetNormalized: Float, // Offset vertical/horizontal normalizado (0.0f a 1.0f)
    val zoomScale: Float,              // Nível de zoom salvo (ex: 1.0f = ajustado à tela)
    val scrollMode: ScrollMode,        // Direção de rolagem preferida para o documento
    val renderTheme: PageRenderTheme,  // Tema de cor aplicado na GPU via ColorMatrix
    val updatedAtEpochMillis: Long     // Timestamp do último salvamento automático
) {
    val completionFraction: Float
        get() = if (totalPages <= 0) 0f else ((currentPageIndex + 1).toFloat() / totalPages).coerceIn(0f, 1f)
}

enum class ScrollMode {
    CONTINUOUS_VERTICAL,
    CONTINUOUS_HORIZONTAL
}

enum class PageRenderTheme {
    ORIGINAL, // Sem filtro de cor
    DARK,     // Inversão de luminância preservando contraste para leitura noturna
    SEPIA     // Matriz de cor quente de baixo contraste azul
}
```

### 1.3. Marcador de Página (`Bookmark`)
Representa um marcador simples vinculado a uma página de um documento.

```kotlin
package com.cl0udshifter.pdfeasy.core.model

data class Bookmark(
    val id: Long,                     // ID gerado pelo Room (0L antes de persistir)
    val documentId: String,           // FK para PdfDocumentItem.id
    val pageIndex: Int,               // Índice da página marcada (base 0)
    val label: String?,               // Rótulo opcional (null usa "Página X")
    val createdAtEpochMillis: Long    // Timestamp de criação
)
```

### 1.4. Geometria, Renderização e Busca (`PageSize`, `TileRegion`, `TextSearchMatch`)
Modelos usados pelo motor de renderização a 120FPS para suportar páginas de dimensões variadas, renderização por tiles em zoom alto e busca textual com destaque de coordenadas.

```kotlin
package com.cl0udshifter.pdfeasy.core.model

data class PageDimensions(
    val pageIndex: Int,
    val widthPoints: Int,
    val heightPoints: Int
) {
    val aspectRatio: Float
        get() = if (heightPoints == 0) 1f else widthPoints.toFloat() / heightPoints.toFloat()
}

data class NormalizedRect(
    val left: Float,   // 0.0f..1.0f relativo à largura da página
    val top: Float,    // 0.0f..1.0f relativo à altura da página
    val right: Float,  // 0.0f..1.0f
    val bottom: Float  // 0.0f..1.0f
)

data class TileRequest(
    val pageIndex: Int,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val cropNormalized: NormalizedRect = NormalizedRect(0f, 0f, 1f, 1f)
)

data class TextSearchMatch(
    val pageIndex: Int,
    val matchIndexInPage: Int,
    val snippet: String,
    val boundsNormalized: List<NormalizedRect> // Caixas delimitadoras do termo na página
)
```

### 1.5. Erros de Domínio (`PdfCoreError`)
Hierarquia selada para tratamento explícito de falhas sem exceções não verificadas na UI.

```kotlin
package com.cl0udshifter.pdfeasy.core.model

sealed interface PdfCoreError {
    data object PasswordRequired : PdfCoreError
    data object InvalidPassword : PdfCoreError
    data object FileNotFoundOrMoved : PdfCoreError
    data object PermissionDenied : PdfCoreError
    data class CorruptedOrUnsupportedPdf(val reason: String) : PdfCoreError
    data class IoFailure(val message: String) : PdfCoreError
}

sealed interface CoreResult<out T> {
    data class Success<T>(val data: T) : CoreResult<T>
    data class Failure(val error: PdfCoreError) : CoreResult<Nothing>
}
```

---

## 2. Interfaces Expostas para a UI (`core.domain` & `core.pdf`)

### 2.1. Sessão e Motor de Renderização de PDF (`PdfSession` & `PdfEngine`)
Gerencia o ciclo de vida de um documento aberto, o pool de `Bitmap` para zero alocações no frame loop de 120FPS e a busca textual em background.

```kotlin
package com.cl0udshifter.pdfeasy.core.pdf

import android.graphics.Bitmap
import com.cl0udshifter.pdfeasy.core.model.*
import kotlinx.coroutines.flow.Flow

interface PdfEngine {
    /**
     * Abre uma sessão de documento a partir de uma URI (SAF ou ACTION_VIEW).
     * Retorna PdfCoreError.PasswordRequired se o PDF exigir senha e password for null,
     * ou PdfCoreError.InvalidPassword se a senha fornecida estiver incorreta.
     */
    suspend fun openSession(
        uriString: String,
        password: String? = null
    ): CoreResult<PdfSession>
}

interface PdfSession : AutoCloseable {
    val documentUri: String
    val pageCount: Int

    /**
     * Retorna as dimensões originais da página em pontos para cálculo de layout contínuo
     * (suporta PDFs com páginas de tamanhos e orientações variados).
     */
    suspend fun getPageDimensions(pageIndex: Int): CoreResult<PageDimensions>

    /**
     * Renderiza uma página inteira (baixa/média resolução) ou um recorte (TileRequest em zoom alto)
     * reutilizando Bitmaps do pool interno (ARGB_8888 / RGB_565).
     */
    suspend fun renderTile(request: TileRequest): CoreResult<Bitmap>

    /**
     * Devolve um Bitmap que saiu da viewport ao pool de memória para evitar GC pauses a 120FPS.
     */
    fun recycleBitmap(bitmap: Bitmap)

    /**
     * Busca um termo ao longo do documento emitindo resultados progressivamente por página.
     * Cancelar a coroutine interrompe imediatamente a varredura nas páginas restantes.
     */
    fun search(query: String): Flow<TextSearchMatch>

    /**
     * Libera file descriptors, instâncias nativas e o pool de Bitmaps da sessão.
     */
    override fun close()
}
```

### 2.2. Repositório da Biblioteca Local (`LibraryRepository`)
Gerencia os documentos importados via SAF ou abertos via `Intent.ACTION_VIEW`, validando permissões persistentes e arquivos movidos/deletados.

```kotlin
package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.*
import kotlinx.coroutines.flow.Flow

data class LibraryItemWithProgress(
    val document: PdfDocumentItem,
    val progress: ReadingProgress?
)

interface LibraryRepository {
    /**
     * Observa todos os documentos da biblioteca local ordenados por última leitura/adição,
     * combinados com o respectivo progresso de leitura.
     */
    fun observeLibrary(searchQuery: String = ""): Flow<List<LibraryItemWithProgress>>

    /**
     * Registra ou atualiza um PDF aberto via SAF ou Intent externo (ACTION_VIEW),
     * tentando adquirir takePersistableUriPermission quando suportado pelo provedor.
     */
    suspend fun registerOrUpdateDocument(uriString: String): CoreResult<PdfDocumentItem>

    /**
     * Atualiza metadados conhecidos após abertura bem-sucedida da sessão (total de páginas, flag de senha, timestamp).
     */
    suspend fun updateDocumentMetadata(
        documentId: String,
        pageCount: Int,
        isPasswordProtected: Boolean,
        lastOpenedAtEpochMillis: Long
    )

    /**
     * Verifica no ContentResolver se os documentos cadastrados continuam acessíveis,
     * marcando arquivos deletados/movidos como MISSING_OR_MOVED sem travar ou remover o histórico.
     */
    suspend fun refreshAvailability()

    /**
     * Remove um documento da biblioteca local (e em cascata seu progresso e marcadores no Room,
     * sem apagar o arquivo original do dispositivo).
     */
    suspend fun removeFromLibrary(documentId: String)
}
```

### 2.3. Repositório de Progresso de Leitura (`ReadingProgressRepository`)
Persiste e observa o estado de leitura com debounce para salvamento contínuo durante o scroll/zoom.

```kotlin
package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.*
import kotlinx.coroutines.flow.Flow

interface ReadingProgressRepository {
    /**
     * Observa o progresso de leitura de um documento específico.
     */
    fun observeProgress(documentId: String): Flow<ReadingProgress?>

    /**
     * Obtém o snapshot mais recente do progresso ao abrir o leitor para restaurar página, offset, zoom e tema.
     */
    suspend fun getProgress(documentId: String): ReadingProgress?

    /**
     * Salva ou atualiza o estado de leitura (página atual, offset normalizado, zoom, modo de scroll e tema).
     */
    suspend fun saveProgress(progress: ReadingProgress)
}
```

### 2.4. Repositório de Marcadores (`BookmarkRepository`)
Gerencia marcadores de página por documento.

```kotlin
package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    /**
     * Observa todos os marcadores de um documento ordenados por pageIndex crescente.
     */
    fun observeBookmarks(documentId: String): Flow<List<Bookmark>>

    /**
     * Observa se uma página específica está marcada.
     */
    fun observeIsBookmarked(documentId: String, pageIndex: Int): Flow<Boolean>

    /**
     * Alterna (adiciona ou remove) o marcador da página informada. Retorna true se ficou marcada.
     */
    suspend fun toggleBookmark(documentId: String, pageIndex: Int, label: String? = null): Boolean

    /**
     * Remove um marcador específico.
     */
    suspend fun deleteBookmark(bookmarkId: Long)
}
```

---

## 3. Invariantes da Camada Core

1. **Zero Bloqueio na Main Thread:** Todos os métodos `suspend` e fluxos (`Flow`) que acessam `ContentResolver`, `Room` ou `PdfSession` executam em `Dispatchers.IO` ou em um dispatcher dedicado de thread única por sessão de PDF (quando exigido pela sincronização nativa do motor PDF).
2. **Índices Base-0:** `pageIndex` e `currentPageIndex` são sempre baseados em zero (`0 until pageCount`). A conversão para exibição (`pageIndex + 1`) ocorre exclusivamente na camada de UI.
3. **Coordenadas Normalizadas (`0.0f..1.0f`):** `scrollOffsetNormalized`, `TileRequest.cropNormalized` e `TextSearchMatch.boundsNormalized` são independentes da densidade de pixels (DPI) e do nível de zoom atual da tela.
4. **Temas na GPU:** `PageRenderTheme` (`ORIGINAL`, `DARK`, `SEPIA`) é persistido no Core, mas aplicado pela UI no `DrawScope` / `Paint` via `ColorMatrixColorFilter`, mantendo os bitmaps do cache em formato original para troca instantânea de tema sem re-renderizar páginas.
