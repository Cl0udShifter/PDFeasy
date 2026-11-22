package com.cl0udshifter.pdfeasy

import android.app.Application

class PdfEasyApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }
}
