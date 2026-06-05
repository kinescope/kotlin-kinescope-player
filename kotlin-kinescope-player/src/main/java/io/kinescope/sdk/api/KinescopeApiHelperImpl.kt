package io.kinescope.sdk.api

import io.kinescope.sdk.models.players.KinescopeCreatePlayerRequest
import io.kinescope.sdk.models.players.KinescopeUpdatePlayerRequest
import kotlinx.coroutines.flow.flow

class KinescopeApiHelperImpl (private val  apiService: KinescopeApi) : KinescopeApiHelper {
    override fun getAllVideos() = flow {
        emit(apiService.getAll())
    }

    override fun getPlayers() = flow {
        emit(apiService.getPlayers())
    }

    override fun getPlayer(playerId: String) = flow {
        emit(apiService.getPlayer(playerId))
    }

    override fun createPlayer(request: KinescopeCreatePlayerRequest) = flow {
        emit(apiService.createPlayer(request))
    }

    override fun updatePlayer(playerId: String, request: KinescopeUpdatePlayerRequest) = flow {
        emit(apiService.updatePlayer(playerId, request))
    }

    override fun deletePlayer(playerId: String) = flow {
        emit(apiService.deletePlayer(playerId))
    }
}