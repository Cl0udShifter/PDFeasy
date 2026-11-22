package com.cl0udshifter.pdfeasy.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.cl0udshifter.pdfeasy.R

object PdfEasyIcons {
    @DrawableRes val Add: Int = R.drawable.ic_add

    @DrawableRes val Search: Int = R.drawable.ic_search

    @DrawableRes val Close: Int = R.drawable.ic_close

    @DrawableRes val ArrowBack: Int = R.drawable.ic_arrow_back

    @DrawableRes val ChevronUp: Int = R.drawable.ic_chevron_up

    @DrawableRes val ChevronDown: Int = R.drawable.ic_chevron_down

    @DrawableRes val Lock: Int = R.drawable.ic_lock

    @DrawableRes val Warning: Int = R.drawable.ic_warning

    @DrawableRes val BookmarkOutline: Int = R.drawable.ic_bookmark_outline

    @DrawableRes val BookmarkFilled: Int = R.drawable.ic_bookmark_filled

    @DrawableRes val BookmarksList: Int = R.drawable.ic_bookmarks_list

    @DrawableRes val ScrollVertical: Int = R.drawable.ic_scroll_vertical

    @DrawableRes val ScrollHorizontal: Int = R.drawable.ic_scroll_horizontal

    @DrawableRes val DocumentEmpty: Int = R.drawable.ic_document_empty

    @DrawableRes val DeleteOutline: Int = R.drawable.ic_delete

    @DrawableRes val MoreVertical: Int = R.drawable.ic_more_vertical

    @DrawableRes val Visibility: Int = R.drawable.ic_visibility

    @DrawableRes val VisibilityOff: Int = R.drawable.ic_visibility_off

    @Composable
    fun painter(
        @DrawableRes iconRes: Int,
    ): Painter = painterResource(id = iconRes)
}
