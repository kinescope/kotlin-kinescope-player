package io.kinescope.sdk.api

import io.kinescope.sdk.models.common.KinescopeAllVideosResponse
import io.kinescope.sdk.models.common.KinescopePlayerTemplateResponse
import io.kinescope.sdk.models.common.KinescopePlayersListResponse
import io.kinescope.sdk.models.players.KinescopeCreatePlayerRequest
import io.kinescope.sdk.models.players.KinescopeDeletePlayerResponse
import io.kinescope.sdk.models.players.KinescopeUpdatePlayerRequest
import kotlinx.coroutines.flow.Flow

interface KinescopeApiHelper {
    fun getAllVideos(): Flow<KinescopeAllVideosResponse>

    fun getPlayers(): Flow<KinescopePlayersListResponse>

    fun getPlayer(playerId: String): Flow<KinescopePlayerTemplateResponse>

    fun createPlayer(request: KinescopeCreatePlayerRequest): Flow<KinescopePlayerTemplateResponse>

    fun updatePlayer(
        playerId: String,
        request: KinescopeUpdatePlayerRequest,
    ): Flow<KinescopePlayerTemplateResponse>

    fun deletePlayer(playerId: String): Flow<KinescopeDeletePlayerResponse>
}