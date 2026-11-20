package com.aditya.swipeclean

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val dateTakenMs: Long,
    val dateAddedMs: Long,
    val durationMs: Long,
    val mime: String
)

data class Meta(
    val dateTaken: Long?,
    val description: String,
    val make: String,
    val model: String,
    val lat: Double?,
    val lng: Double?
) {
    val hasLocation get() = lat != null && lng != null
}
