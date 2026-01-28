package com.kotlin.kinescope.shorts.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.PlaybackException


class InternetConnection(private val context: Context) {

    companion object {
        @Volatile
        private var isToastShown = false
    }


    fun showNoInternetMessage() {
        if (!isToastShown) {
            synchronized(InternetConnection::class.java){
                if (!isToastShown){
                    isToastShown = true
                    Toast.makeText(context, "Нет интернет-соединения. Проверьте настройки.", Toast.LENGTH_SHORT).show()
                    Handler(Looper.getMainLooper()).postDelayed({
                        isToastShown = false
                    }, 30000)
                }
            }
        }
    }

     fun showErrorMessage(error: PlaybackException) {
        val errorMessage = when {
            error.message == null || error.message!!.isEmpty() -> {
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Проблема с интернет-соединением"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Ошибка загрузки видео"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Поврежденный файл видео"
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Ошибка формата видео"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Ошибка инициализации декодера"
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "Декодер не поддерживается"
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "Ошибка аудио-трека"

                    else -> "Ошибка воспроизведения"
                }
            }
            error.message!!.contains("runtime", ignoreCase = true) -> "Ошибка воспроизведения. Попробуйте перезапустить видео"
            error.message!!.contains("codec", ignoreCase = true) -> "Проблема с декодером. Попробуйте другое видео"
            error.message!!.contains("network", ignoreCase = true) -> "Проблема с интернет-соединением"
            else -> error.message
        }
        
        Toast.makeText(context, "Ошибка воспроизведения: $errorMessage", Toast.LENGTH_LONG)
            .show()
    }
}