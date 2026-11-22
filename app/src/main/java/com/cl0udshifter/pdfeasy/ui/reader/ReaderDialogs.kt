package com.cl0udshifter.pdfeasy.ui.reader

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.Bookmark
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons
import com.cl0udshifter.pdfeasy.ui.library.LibraryFormatters

private object DialogDimens {
    val SheetHorizontal = 20.dp
    val SheetBottom = 28.dp
    val Spacing = 12.dp
    val SmallSpacing = 8.dp
    val RowMinHeight = 52.dp
    val MinTouchSize = 48.dp
    val IconSize = 18.dp
    val ErrorIconSize = 36.dp
    val ErrorMaxWidth = 300.dp
}

@Composable
fun PasswordPromptDialog(
    displayName: String,
    promptState: PasswordPromptState,
    onDismissToLibrary: () -> Unit,
    onSubmitPassword: (String) -> Unit,
) {
    if (!promptState.isVisible) return

    var passwordText by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissToLibrary,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(text = stringResource(R.string.password_dialog_title), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            PasswordDialogBody(
                displayName = displayName,
                passwordText = passwordText,
                showPassword = showPassword,
                promptState = promptState,
                onPasswordChange = { passwordText = it },
                onToggleVisibility = { showPassword = !showPassword },
                onSubmitPassword = onSubmitPassword,
            )
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPassword(passwordText) },
                enabled = passwordText.isNotEmpty() && !promptState.isSubmitting,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(text = stringResource(R.string.password_unlock), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissToLibrary) {
                Text(text = stringResource(R.string.password_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun PasswordDialogBody(
    displayName: String,
    passwordText: String,
    showPassword: Boolean,
    promptState: PasswordPromptState,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSubmitPassword: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(DialogDimens.Spacing)) {
        Text(
            text = stringResource(R.string.password_dialog_subtitle, LibraryFormatters.cleanDisplayName(displayName)),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = passwordText,
            onValueChange = onPasswordChange,
            singleLine = true,
            enabled = !promptState.isSubmitting,
            isError = promptState.isInvalidAttempt,
            label = { Text(text = stringResource(R.string.password_field_label)) },
            supportingText =
                if (promptState.isInvalidAttempt) {
                    { Text(text = stringResource(R.string.password_invalid_error)) }
                } else {
                    null
                },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (passwordText.isNotEmpty() && !promptState.isSubmitting) {
                            onSubmitPassword(passwordText)
                        }
                    },
                ),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    val icon = if (showPassword) PdfEasyIcons.VisibilityOff else PdfEasyIcons.Visibility
                    Icon(
                        painter = PdfEasyIcons.painter(icon),
                        contentDescription = stringResource(R.string.password_toggle_visibility),
                        modifier = Modifier.size(DialogDimens.IconSize),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun BookmarksBottomSheet(
    isOpen: Boolean,
    bookmarks: List<Bookmark>,
    currentPageIndex: Int,
    isCurrentPageBookmarked: Boolean,
    onDismiss: () -> Unit,
    onToggleCurrentPageBookmark: () -> Unit,
    onSelectBookmark: (Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    if (!isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DialogDimens.SheetHorizontal)
                    .padding(bottom = DialogDimens.SheetBottom),
            verticalArrangement = Arrangement.spacedBy(DialogDimens.Spacing),
        ) {
            BookmarksSheetHeader(
                currentPageIndex = currentPageIndex,
                isCurrentPageBookmarked = isCurrentPageBookmarked,
                onToggleCurrentPageBookmark = onToggleCurrentPageBookmark,
            )
            BookmarksListBody(
                bookmarks = bookmarks,
                onSelectBookmark = onSelectBookmark,
                onDeleteBookmark = onDeleteBookmark,
            )
        }
    }
}

@Composable
private fun BookmarksSheetHeader(
    currentPageIndex: Int,
    isCurrentPageBookmarked: Boolean,
    onToggleCurrentPageBookmark: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.reader_bookmarks_title), style = MaterialTheme.typography.titleLarge)
        if (!isCurrentPageBookmarked) {
            TextButton(onClick = onToggleCurrentPageBookmark) {
                Text(
                    text = stringResource(R.string.reader_bookmarks_add_current, currentPageIndex + 1),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BookmarksListBody(
    bookmarks: List<Bookmark>,
    onSelectBookmark: (Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = DialogDimens.SheetBottom),
        ) {
            Text(
                text = stringResource(R.string.reader_bookmarks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DialogDimens.SmallSpacing)) {
            items(items = bookmarks, key = { it.id }) { bookmark ->
                BookmarkItemRow(
                    bookmark = bookmark,
                    onSelect = { onSelectBookmark(bookmark.pageIndex) },
                    onDelete = { onDeleteBookmark(bookmark.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BookmarkItemRow(
    bookmark: Bookmark,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val label =
        bookmark.label?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.reader_bookmarks_page_label_format, bookmark.pageIndex + 1)
    Row(
        modifier = Modifier.fillMaxWidth().height(DialogDimens.RowMinHeight).clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.BookmarkFilled),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(DialogDimens.IconSize),
        )
        Spacer(modifier = Modifier.size(DialogDimens.Spacing))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.reader_bookmarks_page_label_format, bookmark.pageIndex + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(DialogDimens.MinTouchSize)) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.DeleteOutline),
                contentDescription = stringResource(R.string.reader_bookmark_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DialogDimens.IconSize),
            )
        }
    }
}

@Composable
fun ReaderFatalErrorScreen(
    error: PdfCoreError,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailText =
        when (error) {
            PdfCoreError.FileNotFoundOrMoved -> stringResource(R.string.reader_error_not_found)
            PdfCoreError.PermissionDenied -> stringResource(R.string.reader_error_permission)
            is PdfCoreError.CorruptedOrUnsupportedPdf -> stringResource(R.string.reader_error_corrupted)
            is PdfCoreError.IoFailure -> stringResource(R.string.reader_error_io)
            PdfCoreError.PasswordRequired, PdfCoreError.InvalidPassword ->
                stringResource(R.string.password_invalid_error)
        }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(DialogDimens.SheetHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(DialogDimens.ErrorIconSize),
            )
            Spacer(modifier = Modifier.height(DialogDimens.Spacing))
            Text(
                text = stringResource(R.string.reader_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(DialogDimens.SmallSpacing))
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = DialogDimens.ErrorMaxWidth),
            )
            Spacer(modifier = Modifier.height(DialogDimens.SheetHorizontal))
            OutlinedButton(
                onClick = onBackToLibrary,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.height(DialogDimens.MinTouchSize),
            ) {
                Text(text = stringResource(R.string.reader_error_back), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
