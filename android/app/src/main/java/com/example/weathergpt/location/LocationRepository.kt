package com.example.weathergpt.location

import android.content.Context

object LocationRepository {

    fun getSavedLocation(
        context: Context
    ): SelectedLocation {
        return LocationStore.getLocation(context)
    }

    fun saveLocation(
        context: Context,
        location: SelectedLocation
    ) {
        LocationStore.saveLocation(
            context,
            location
        )
    }
}
