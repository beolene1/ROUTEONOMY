package com.routeonomy.app.data

import android.content.Context
import com.routeonomy.app.domain.Region
import com.routeonomy.app.domain.TripContext

/** Loads region candidates and the demo trip bundled in assets. */
class AssetRouteRepository(private val context: Context) {
    fun regions(): List<Region> = RouteJsonParser.regions(read("regions.json"))
    fun demoTrip(): TripContext = RouteJsonParser.trip(read("demo_trip.json"))

    private fun read(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }
}
