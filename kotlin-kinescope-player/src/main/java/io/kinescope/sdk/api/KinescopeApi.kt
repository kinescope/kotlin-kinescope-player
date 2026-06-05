package io.kinescope.sdk.api

import io.kinescope.sdk.models.common.KinescopeAllVideosResponse
import io.kinescope.sdk.models.common.KinescopePlayerTemplateResponse
import io.kinescope.sdk.models.common.KinescopePlayersListResponse
import io.kinescope.sdk.models.players.KinescopeCreatePlayerRequest
import io.kinescope.sdk.models.players.KinescopeDeletePlayerResponse
import io.kinescope.sdk.models.players.KinescopeUpdatePlayerRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface KinescopeApi {
    @GET(KinescopeApiConfig.VIDEOS)
    suspend fun getAll(): KinescopeAllVideosResponse

    @GET(KinescopeApiConfig.PLAYERS)
    suspend fun getPlayers(): KinescopePlayersListResponse

    @GET(KinescopeApiConfig.PLAYER_BY_ID)
    suspend fun getPlayer(@Path(KinescopeApiConfig.PLAYER_ID_PARAM) playerId: String): KinescopePlayerTemplateResponse

    @POST(KinescopeApiConfig.PLAYERS)
    suspend fun createPlayer(@Body request: KinescopeCreatePlayerRequest): KinescopePlayerTemplateResponse

    @PUT(KinescopeApiConfig.PLAYER_BY_ID)
    suspend fun updatePlayer(
        @Path(KinescopeApiConfig.PLAYER_ID_PARAM) playerId: String,
        @Body request: KinescopeUpdatePlayerRequest,
    ): KinescopePlayerTemplateResponse

    @DELETE(KinescopeApiConfig.PLAYER_BY_ID)
    suspend fun deletePlayer(@Path(KinescopeApiConfig.PLAYER_ID_PARAM) playerId: String): KinescopeDeletePlayerResponse
}
