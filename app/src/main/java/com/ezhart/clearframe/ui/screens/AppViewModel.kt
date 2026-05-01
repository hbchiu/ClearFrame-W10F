package com.ezhart.clearframe.ui.screens

import android.app.Application
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ezhart.clearframe.ClearFrameApplication
import com.ezhart.clearframe.MainActivity
import com.ezhart.clearframe.data.DisplayMode
import com.ezhart.clearframe.data.PhotoRepository
import com.ezhart.clearframe.data.loadConfig
import com.ezhart.clearframe.sync.ReloadRequest
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import android.content.res.Configuration

private const val TAG = "ViewModel"

sealed interface AppUiState {
    data object Error : AppUiState
    data object Loading : AppUiState
    data object Empty : AppUiState

    data class Running(val slideShowViewModel: SlideshowViewModel) : AppUiState
}

class AppViewModel(
    application: Application,
    private val photoRepository: PhotoRepository
) : AndroidViewModel(application) {

    var uiState: AppUiState by mutableStateOf(AppUiState.Loading)
    var displayMode: DisplayMode by mutableStateOf(DisplayMode.ADAPTIVE)
    private var slideshowViewModel: SlideshowViewModel? = null

    init {
        EventBus.getDefault().register(this)
        displayMode = loadConfig(application).displayMode
        getPhotos()
    }

    fun getPhotos() {
        uiState = AppUiState.Loading
        viewModelScope.launch {
            try {
                val allPhotos = photoRepository.getPhotos()

                val photos = if (displayMode == DisplayMode.MATCH_ORIENTATION) {
                    val frameIsPortrait = getApplication<Application>().resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    allPhotos.filter { photo ->
                        photo.isVideo || (photo.isPortrait == frameIsPortrait)
                    }.also { filtered ->
                        Log.d(TAG, "MATCH_ORIENTATION: frameIsPortrait=$frameIsPortrait, total=${allPhotos.size}, filtered=${filtered.size}")
                    }
                } else {
                    allPhotos
                }

                uiState = if (photos.isEmpty()) {
                    AppUiState.Empty
                } else {
                    if (slideshowViewModel != null) {
                        slideshowViewModel?.cleanup()
                    }

                    val vm = SlideshowViewModel(photos.toMutableList())
                    slideshowViewModel = vm

                    AppUiState.Running(vm)
                }

            } catch (e: Exception) {
                uiState = AppUiState.Error
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as ClearFrameApplication)
                AppViewModel(
                    application = application,
                    photoRepository = application.container.photoRepository
                )
            }
        }
    }

    @Subscribe
    public fun handleRemoteButton(event: MainActivity.RemoteKeyPressEvent) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> reload()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public fun handleReloadRequest(event: ReloadRequest) {
        Log.d(TAG, "Reload requested ${event.reason}")
        reload()
    }

    fun reload() {
        getPhotos()
    }
}