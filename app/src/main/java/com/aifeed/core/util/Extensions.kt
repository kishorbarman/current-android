package com.aifeed.core.util

import android.content.Context
import android.widget.Toast
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Instant.formatRelativeTime(): String {
    val now = Instant.now()
    val duration = Duration.between(this, now)

    return when {
        duration.toMinutes() < 1 -> "Just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        duration.toDays() < 30 -> "${duration.toDays() / 7}w ago"
        else -> formatDate("MMM d")
    }
}

fun Instant.formatDate(pattern: String = "MMM d, yyyy"): String {
    val formatter = DateTimeFormatter.ofPattern(pattern)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(this)
}

fun Instant.formatDateTime(): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(this)
}

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (length > maxLength) {
        take(maxLength - ellipsis.length) + ellipsis
    } else {
        this
    }
}

fun String?.orEmpty(default: String = ""): String = this ?: default

inline fun <T> T?.orElse(block: () -> T): T = this ?: block()
