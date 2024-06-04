package com.example.osmap.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface MapService {
    @POST("/map_route")
    fun getRoute(
        @Query("algorithm") algorithm: String,
        @Body request: RouteRequest
    ): Call<RouteResponse>
}

data class RouteRequest(
    val start: List<Double>,
    val end: List<Double>
)

data class RouteResponse(
    val algorithm: String,
    val ruta: List<List<Double>>
)