package com.uniandes.sport.cache

import com.google.firebase.Timestamp
import com.uniandes.sport.models.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.Date

class OpenMatchLocationCacheTest {
    @Before
    fun setUp() {
        OpenMatchLocationCache.clear()
    }

    @Test
    fun cachesAndOrdersRecentAndPopularSuggestions() {
        val historyEvents = listOf(
            event(
                id = "e1",
                location = "Court A - Lat: 4.6010, Lng: -74.0640",
                scheduledAtMillis = 3_000L,
                membersCount = 6,
                maxParticipants = 10
            ),
            event(
                id = "e2",
                location = "Court B - Lat: 4.6020, Lng: -74.0630",
                scheduledAtMillis = 2_000L,
                membersCount = 8,
                maxParticipants = 10
            ),
            event(
                id = "e3",
                location = "Court A - Lat: 4.6010, Lng: -74.0640",
                scheduledAtMillis = 1_000L,
                membersCount = 7,
                maxParticipants = 10
            )
        )

        val allEvents = historyEvents + listOf(
            event(
                id = "e4",
                location = "Stadium C - Lat: 4.7000, Lng: -74.0500",
                scheduledAtMillis = 4_000L,
                membersCount = 18,
                maxParticipants = 20
            )
        )

        val first = OpenMatchLocationCache.getSuggestions(
            userId = "user-1",
            historyEvents = historyEvents,
            allEvents = allEvents
        )
        val second = OpenMatchLocationCache.getSuggestions(
            userId = "user-1",
            historyEvents = historyEvents,
            allEvents = allEvents
        )

        assertSame(first, second)
        assertEquals(2, first.size)
        assertEquals(OpenMatchLocationSuggestionType.RECENT, first[0].type)
        assertEquals("Court A", first[0].label)
        assertEquals(OpenMatchLocationSuggestionType.POPULAR, first[1].type)
        assertEquals("Stadium C", first[1].label)
        assertEquals(1, OpenMatchLocationCache.hitCount)
    }

    private fun event(
        id: String,
        location: String,
        scheduledAtMillis: Long,
        membersCount: Long,
        maxParticipants: Long
    ): Event {
        return Event(
            id = id,
            location = location,
            scheduledAt = Timestamp(Date(scheduledAtMillis)),
            membersCount = membersCount,
            maxParticipants = maxParticipants,
            status = "active"
        )
    }
}