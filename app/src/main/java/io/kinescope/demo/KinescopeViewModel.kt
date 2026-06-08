package io.kinescope.demo

import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import io.kinescope.sdk.api.KinescopeApiHelper
import io.kinescope.sdk.models.videos.KinescopeVideoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


class KinescopeViewModel(private val apiHelper: KinescopeApiHelper) : ViewModel() {
    private companion object {
        const val TAG = "KinescopeViewModel"
    }
    private val _allVideos: MutableLiveData<ArrayList<KinescopeVideoApi>> =
        MutableLiveData(arrayListOf())
    val allVideos: LiveData<ArrayList<KinescopeVideoApi>>
        get() = _allVideos

    fun getAllVideos() {
        viewModelScope.launch {
            apiHelper.getAllVideos().flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e(TAG, "Failed to load videos", e)
                }
                .collect { response ->
                    Log.d(TAG, "Loaded ${response.data.size} videos")
                    _allVideos.value = ArrayList(response.data)
                }
        }
    }

    class Factory(private val apiHelper: KinescopeApiHelper) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras
        ): T {
            return KinescopeViewModel(apiHelper) as T
        }
    }
}

