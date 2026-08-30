package com.example.weathergpt.location

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


object LocationStore {

    private const val PREF_NAME =
        "weathergpt_preferences"

    private const val NAME =
        "selected_location_name"

    private const val LATITUDE =
        "selected_location_latitude"

    private const val LONGITUDE =
        "selected_location_longitude"

    private const val COUNTRY =
        "selected_location_country"

    private const val ADMIN1 =
        "selected_location_admin1"

    private const val TIMEZONE =
        "selected_location_timezone"

    private const val LOCATION_MODE =
        "location_mode"

    private const val MODE_GPS =
        "gps"

    private const val MODE_MANUAL =
        "manual"

    private const val DEFAULT_NAME =
        "Current location"

    private const val DEFAULT_LAT =
        16.5062

    private const val DEFAULT_LON =
        80.6480

    private val _location =
        MutableStateFlow<SelectedLocation?>(null)

    val location: StateFlow<SelectedLocation?> =
        _location.asStateFlow()


    fun getLocation(
        context: Context
    ): SelectedLocation {

        val preferences =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        return SelectedLocation(
            name =
                preferences.getString(
                    NAME,
                    DEFAULT_NAME
                ) ?: DEFAULT_NAME,

            latitude =
                preferences.getFloat(
                    LATITUDE,
                    DEFAULT_LAT.toFloat()
                ).toDouble(),

            longitude =
                preferences.getFloat(
                    LONGITUDE,
                    DEFAULT_LON.toFloat()
                ).toDouble(),

            country =
                preferences.getString(
                    COUNTRY,
                    null
                ),

            admin1 =
                preferences.getString(
                    ADMIN1,
                    null
                ),

            timezone =
                preferences.getString(
                    TIMEZONE,
                    null
                )
        )
    }


    fun initialize(
        context: Context
    ) {

        _location.value =
            getLocation(
                context
            )
    }


    fun saveLocation(
        context: Context,
        location: SelectedLocation,
        manual: Boolean = true
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                NAME,
                location.name
            )
            .putFloat(
                LATITUDE,
                location.latitude.toFloat()
            )
            .putFloat(
                LONGITUDE,
                location.longitude.toFloat()
            )
            .putString(
                COUNTRY,
                location.country
            )
            .putString(
                ADMIN1,
                location.admin1
            )
            .putString(
                TIMEZONE,
                location.timezone
            )
            .putString(
                LOCATION_MODE,
                if (manual) {
                    MODE_MANUAL
                } else {
                    MODE_GPS
                }
            )
            .apply()

        _location.value =
            location
    }


    fun useGps(
        context: Context,
        location: SelectedLocation
    ) {

        saveLocation(
            context = context,
            location = location,
            manual = false
        )
    }


    fun isManual(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                LOCATION_MODE,
                MODE_GPS
            ) == MODE_MANUAL
    }
}
