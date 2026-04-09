package com.uniandes.sport.ui.components

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.uniandes.sport.patterns.event.PhoneCalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberPhoneCalendarEventsState(daysAhead: Int = 14): State<List<PhoneCalendarEvent>> {
    val context = LocalContext.current
    val eventsState = remember { mutableStateOf<List<PhoneCalendarEvent>>(emptyList()) }
    val hasCalendarPermissionState = remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermissionState.value = granted
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            hasCalendarPermissionState.value = true
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    LaunchedEffect(hasCalendarPermissionState.value, daysAhead) {
        if (!hasCalendarPermissionState.value) {
            eventsState.value = emptyList()
            return@LaunchedEffect
        }

        eventsState.value = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val begin = System.currentTimeMillis()
            val end = begin + daysAhead * 24L * 60L * 60L * 1000L

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, begin)
            ContentUris.appendId(builder, end)

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            )

            val result = mutableListOf<PhoneCalendarEvent>()
            resolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)

                while (cursor.moveToNext()) {
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) else "Busy"
                    val startMillis = if (beginIdx >= 0) cursor.getLong(beginIdx) else 0L
                    val endMillis = if (endIdx >= 0) cursor.getLong(endIdx) else startMillis
                    val isAllDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) == 1

                    // Ignore all-day events: they should not affect open match recommendations.
                    if (startMillis > 0L && !isAllDay) {
                        result += PhoneCalendarEvent(
                            title = title.takeIf { !it.isNullOrBlank() } ?: "Busy",
                            startMillis = startMillis,
                            endMillis = endMillis,
                            isAllDay = isAllDay
                        )
                    }
                }
            }
            result
        }
    }

    return eventsState
}
