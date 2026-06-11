package com.example.thresholdterm_002.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.thresholdterm_002.data.model.LibraryPlace

class GoogleMapNavigator {

    fun openPlace(context: Context, place: LibraryPlace) {
        val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}")
        val googleMapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
        val resolvedIntent = googleMapIntent.takeIf { it.resolveActivity(context.packageManager) != null }
            ?: fallbackIntent.takeIf { it.resolveActivity(context.packageManager) != null }
            ?: webIntent
        context.startActivity(resolvedIntent)
    }
}
