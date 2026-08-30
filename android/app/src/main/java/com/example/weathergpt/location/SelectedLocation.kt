package com.example.weathergpt.location

data class SelectedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?,
    val timezone: String?
)
