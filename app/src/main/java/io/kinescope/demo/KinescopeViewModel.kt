package io.kinescope.demo

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import io.kinescope.sdk.api.KinescopeApiHelper
import io.kinescope.sdk.models.videos.KinescopeVideoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch


class KinescopeViewModel(private val apiHelper: KinescopeApiHelper) : ViewModel() {
    private val _allVideos: MutableLiveData<ArrayList<KinescopeVideoApi>> =
        MutableLiveData(arrayListOf())
    val allVideos: LiveData<ArrayList<KinescopeVideoApi>>
        get() = _allVideos

    fun getAllVideos() {
        viewModelScope.launch {
            apiHelper.getAllVideos().flowOn(Dispatchers.IO)
                .catch { e ->
                    e
                }
                .collect() {
                    _allVideos.value = ArrayList(it.data)
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

