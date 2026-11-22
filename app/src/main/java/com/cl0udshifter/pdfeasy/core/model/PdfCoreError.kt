package com.cl0udshifter.pdfeasy.core.model

/**
 * Hierarquia selada de erros de dominio da camada Core.
 */
sealed interface PdfCoreError {
    data object PasswordRequired : PdfCoreError

    data object InvalidPassword : PdfCoreError

    data object FileNotFoundOrMoved : PdfCoreError

    data object PermissionDenied : PdfCoreError

    data class CorruptedOrUnsupportedPdf(
        val reason: String,
    ) : PdfCoreError

    data class IoFailure(
        val message: String,
    ) : PdfCoreError
}

/**
 * Resultado explicito de operacoes do Core sem propagar excecoes nao verificadas para a UI.
 */
sealed interface CoreResult<out T> {
    data class Success<T>(
        val data: T,
    ) : CoreResult<T>

    data class Failure(
        val error: PdfCoreError,
    ) : CoreResult<Nothing>
}
