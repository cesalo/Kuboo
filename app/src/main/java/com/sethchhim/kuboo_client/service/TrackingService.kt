package com.sethchhim.kuboo_client.service

import androidx.work.*
import androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
import com.sethchhim.kuboo_client.BaseApplication
import com.sethchhim.kuboo_client.BuildConfig
import com.sethchhim.kuboo_client.Constants.KEY_LOGIN_NICKNAME
import com.sethchhim.kuboo_client.Constants.KEY_LOGIN_PASSWORD
import com.sethchhim.kuboo_client.Constants.KEY_LOGIN_SERVER
import com.sethchhim.kuboo_client.Constants.KEY_LOGIN_USERNAME
import com.sethchhim.kuboo_client.Constants.TAG_TRACKING_SERVICE
import com.sethchhim.kuboo_client.Settings
import com.sethchhim.kuboo_client.data.ViewModel
import com.sethchhim.kuboo_client.util.SystemUtil
import com.sethchhim.kuboo_remote.KubooRemote
import com.sethchhim.kuboo_remote.model.Book
import com.sethchhim.kuboo_remote.model.Login
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TrackingService {

    init {
        BaseApplication.appComponent.inject(this)
    }

    @Inject lateinit var kubooRemote: KubooRemote
    @Inject lateinit var systemUtil: SystemUtil
    @Inject lateinit var viewModel: ViewModel

    internal fun startTrackingServicePeriodic() {
        val constraints = Constraints.Builder()
                .build()
        val login = viewModel.getActiveLogin()
        val inputData = Data.Builder()
                .putString(KEY_LOGIN_NICKNAME, login.nickname)
                .putString(KEY_LOGIN_SERVER, login.server)
                .putString(KEY_LOGIN_USERNAME, login.username)
                .putString(KEY_LOGIN_PASSWORD, login.password)
                .build()
        val trackingWork = when (BuildConfig.DEBUG) {
            true -> PeriodicWorkRequest
                    .Builder(TrackingWorker::class.java, MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .build()
            false -> PeriodicWorkRequest
                    .Builder(TrackingWorker::class.java, Settings.DOWNLOAD_TRACKING_INTERVAL.toLong(), TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .build()
        }
        WorkManager.getInstance().enqueueUniquePeriodicWork(TAG_TRACKING_SERVICE, ExistingPeriodicWorkPolicy.REPLACE, trackingWork)
    }

    internal fun startTrackingServiceSingle(login: Login) {
        viewModel.getDownloadList(favoriteCompressed = true).observeForever {
            it?.let {
                it
                        .filter { it.isFavorite }
                        .forEach { startTrackingByBook(login, it) }
                kubooRemote.resumeAll()
            }
        }
    }

    internal fun startTrackingByBook(login: Login, book: Book) {
        val isBookServerMatchActiveServer = login.server == book.server
        if (isBookServerMatchActiveServer) {
            Timber.d("Tracking of book start: title[${book.title}] isBookServerMatchActiveServer[$isBookServerMatchActiveServer]")
            val startTime = System.currentTimeMillis()
            viewModel.getSeriesNeighborsRemote(login, book, book.server + book.linkXmlPath, Settings.DOWNLOAD_TRACKING_LIMIT).observeForever {
                it?.let { result ->
                    val mutableResult = result.apply { forEach { it.isFavorite = true } }.toMutableList()
                    handleResult(login, mutableResult, book, startTime)
                }
                        ?: Timber.e("Tracking of book failed to getSeriesNeighborsRemote: title[${book.title}]")
            }
        } else {
            Timber.w("Tracking of book failed to match active server: title[${book.title}] isBookServerMatchActiveServer[$isBookServerMatchActiveServer]")
        }
    }

    private fun handleResult(login: Login, seriesNeighbors: MutableList<Book>, book: Book, startTime: Long) {
        val isRequireNextPage = seriesNeighbors.size < Settings.DOWNLOAD_TRACKING_LIMIT && book.linkNext.isNotEmpty()
        when (isRequireNextPage) {
            true -> getRemainingSeriesNeighbors(login, seriesNeighbors, book, startTime)
            false -> handleResultFinal(login, book, seriesNeighbors, startTime)
        }
    }

    private fun getRemainingSeriesNeighbors(login: Login, seriesNeighbors: MutableList<Book>, book: Book, startTime: Long) {
        val remainingCount = Settings.DOWNLOAD_TRACKING_LIMIT - seriesNeighbors.size
        val url = book.server + book.linkNext
        viewModel.getSeriesNeighborsNextPageRemote(login, url, seriesLimit = remainingCount).observeForever {
            when (it == null) {
                true -> Timber.e("Tracking of book failed to getSeriesNeighborsNextPageRemote: title[${book.title}]")
                false -> {
                    it!!.forEach { it.isFavorite = true }
                    seriesNeighbors.addAll(it)
                }
            }
            handleResultFinal(login, book, seriesNeighbors, startTime)
        }
    }

    private fun handleResultFinal(login: Login, book: Book, seriesNeighbors: MutableList<Book>, startTime: Long) {
        val elapsedTime = System.currentTimeMillis() - startTime
        Timber.d("Tracking of book finished.  title[${book.title}] [$elapsedTime ms]")
        if (seriesNeighbors.isNotEmpty()) {
            val doNotDeleteList = mutableListOf<Book>().apply {
                add(book)
                addAll(seriesNeighbors)
            }
            doNotDeleteList.forEach {
                Timber.d("Tracking do not delete: ${it.title}")
            }
            viewModel.deleteFetchDownloadsNotInList(doNotDeleteList)
            viewModel.startFetchDownloads(login, seriesNeighbors, Settings.DOWNLOAD_SAVE_PATH)
        }
    }

}