package com.sethchhim.kuboo_client.ui.reader.pdf

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import com.artifex.mupdf.mini.DocumentActivity
import com.sethchhim.kuboo_client.Constants
import com.sethchhim.kuboo_client.R
import com.sethchhim.kuboo_client.Settings
import com.sethchhim.kuboo_client.data.ViewModel
import com.sethchhim.kuboo_client.util.SystemUtil
import com.sethchhim.kuboo_remote.model.Book
import dagger.android.AndroidInjection
import org.jetbrains.anko.collections.forEachWithIndex
import org.jetbrains.anko.toast
import timber.log.Timber
import java.io.File
import java.text.DecimalFormat
import javax.inject.Inject

class ReaderPdfActivity : DocumentActivity() {

    @Inject lateinit var systemUtil: SystemUtil
    @Inject lateinit var viewModel: ViewModel

    private lateinit var currentBook: Book

    override fun onCreate(savedInstanceState: Bundle?) {
        forceOrientationSetting()
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setKeepScreenOn()
        currentBook = intent.getParcelableExtra(Constants.ARG_BOOK)
        val uri = Uri.fromFile(File(currentBook.filePath))

        when (uri != null) {
            true -> loadUri(uri)
            false -> {
                toast(getString(R.string.reader_something_went_wrong))
                finish()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (Settings.VOLUME_PAGE_TURN) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (event.isLongPress)
                    onVolumeDownLongPressed()
                else if (event.action == KeyEvent.ACTION_UP)
                    onVolumeDownPressed()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (event.isLongPress)
                    onVolumeUpLongPressed()
                else if (event.action == KeyEvent.ACTION_UP)
                    onVolumeUpPressed()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onLoadPageSuccess() {
        super.onLoadPageSuccess()
        saveBookmark()
    }

    private fun saveBookmark() {
        currentBook.currentPage = currentPage
        currentBook.totalPages = pageCount
        intent.putExtra(Constants.ARG_BOOK, currentBook)
        viewModel.putRemoteUserApi(currentBook)
        viewModel.addRecent(currentBook)
    }

    private fun forceOrientationSetting() {
        when (Settings.SCREEN_ORIENTATION) {
            0 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            1 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            2 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
    }

    private fun onVolumeDownLongPressed() {
        goLast()
    }

    private fun onVolumeDownPressed() {
        goForward()
    }

    private fun onVolumeUpLongPressed() {
        goFirst()
    }

    private fun onVolumeUpPressed() {
        goBackward()
    }

    private fun setKeepScreenOn() {
        if (Settings.KEEP_SCREEN_ON) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun printOutline() {
        flatOutline?.forEachWithIndex { i, item ->
            Timber.d("$i  ${item.title} page[${item.page}] total[${item.totalPages}]")
        } ?: Timber.e("Outline is null")
    }

    private fun formatDecimal(double: Double) = DecimalFormat("####0.000000000000000000").format(double)

}