package dev.hark.smsbridge.core

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object IstTime {

    const val OFFSET_SECONDS = 19_800L
    private val zone = ZoneOffset.ofTotalSeconds(OFFSET_SECONDS.toInt())
    private val isoFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val humanFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun floorWindow(epochMillis: Long, minutes: Int): Long {
        val step = minutes.coerceAtLeast(1) * 60_000L
        val local = epochMillis + OFFSET_SECONDS * 1_000L
        val start = Math.floorDiv(local, step) * step
        return start - OFFSET_SECONDS * 1_000L
    }

    fun windowStart(nowMillis: Long, minutes: Int): Long {
        return floorWindow(nowMillis, minutes)
    }

    fun nextWindowStart(nowMillis: Long, minutes: Int): Long {
        val step = minutes.coerceAtLeast(1) * 60_000L
        return floorWindow(nowMillis, minutes) + step
    }

    fun iso(epochMillis: Long): String {
        return isoFormat.format(Instant.ofEpochMilli(epochMillis).atOffset(zone))
    }

    fun human(epochMillis: Long): String {
        return humanFormat.format(Instant.ofEpochMilli(epochMillis).atOffset(zone))
    }

    fun clock(epochMillis: Long): String {
        return clockFormat.format(Instant.ofEpochMilli(epochMillis).atOffset(zone))
    }

    fun startOfDay(nowMillis: Long): Long {
        return floorWindow(nowMillis, 1440)
    }
}
