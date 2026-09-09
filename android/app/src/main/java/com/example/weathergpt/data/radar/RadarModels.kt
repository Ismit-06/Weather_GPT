package com.example.weathergpt.data.radar

import com.google.gson.annotations.SerializedName

data class RadarFrame(
    @SerializedName("time")
    val time: Long,
    @SerializedName("path")
    val path: String
)

data class RainViewerRadar(
    @SerializedName("past")
    val past: List<RadarFrame>? = null,
    @SerializedName("nowcast")
    val nowcast: List<RadarFrame>? = null
)

data class RainViewerResponse(
    @SerializedName("version")
    val version: String? = null,
    @SerializedName("generated")
    val generated: Long? = null,
    @SerializedName("host")
    val host: String? = null,
    @SerializedName("radar")
    val radar: RainViewerRadar? = null
)

data class RadarMetadata(
    val host: String,
    val frames: List<RadarFrame>,
    val latestTimestamp: Long,
    val lastFetchedAt: Long
)
