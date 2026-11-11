/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 AutoClicky contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.tartaruzz.autoclicky.recording

/*
 * Pure data model of a recording session.
 *
 * Coordinates are in screen pixels and times are absolute event times in milliseconds, which keeps
 * [StrokeCollector] and [ScenarioRecorder] free of any Android dependency so they can be unit tested on the JVM.
 */

/** A single position sampled from a finger, at a given time. */
data class RecordedPoint(
    val x: Float,
    val y: Float,
    val timeMs: Long,
)

/** The complete timeline of one finger, from its down to its up event. */
data class RecordedFinger(
    val points: List<RecordedPoint>,
) {

    val startTimeMs: Long get() = points.first().timeMs
    val endTimeMs: Long get() = points.last().timeMs

    val startPoint: RecordedPoint get() = points.first()

    /** Longest distance between the first point and any other point of this finger. */
    val maxDistanceFromStartPx: Float
        get() = points.maxOfOrNull { point ->
            val dx = point.x - startPoint.x
            val dy = point.y - startPoint.y
            kotlin.math.sqrt(dx * dx + dy * dy)
        } ?: 0f
}

/** All the fingers pressed during one gesture. A new stroke starts as soon as every finger has been released. */
data class RecordedStroke(
    val fingers: List<RecordedFinger>,
) {

    val startTimeMs: Long get() = fingers.minOf { finger -> finger.startTimeMs }
    val endTimeMs: Long get() = fingers.maxOf { finger -> finger.endTimeMs }
    val durationMs: Long get() = endTimeMs - startTimeMs
}

/** The gesture types the recorder can produce. */
sealed interface RecordedAction {

    val startTimeMs: Long
    val endTimeMs: Long

    val durationMs: Long get() = endTimeMs - startTimeMs

    /** A tap: a single finger pressed and released almost immediately, without moving. */
    data class Tap(
        override val startTimeMs: Long,
        override val endTimeMs: Long,
        val x: Float,
        val y: Float,
    ) : RecordedAction

    /** A swipe, a long press or any multi-finger gesture. */
    data class Gesture(
        override val startTimeMs: Long,
        override val endTimeMs: Long,
        val fingers: List<RecordedFinger>,
    ) : RecordedAction
}

/** What a capture is recording, so the UI can tell the user which mode is active. */
enum class RecordingMode(private val singular: String, private val plural: String) {

    /** Taps performed in the app, captured through the pass-through watcher. */
    TAPS("tap", "taps"),

    /** Gestures drawn on the dimmed overlay of the screen. */
    GESTURES("gesture", "gestures");

    /** "1 tap", "12 taps", "3 gestures": the label of a capture of [count] actions. */
    fun label(count: Int): String = if (count == 1) singular else plural
}

/** A finished recording, ready to be saved as a scenario (the AutoClicky template). */
data class CompletedRecording(
    val actions: List<RecordedAction>,
    val mode: RecordingMode = RecordingMode.TAPS,
) {

    val isEmpty: Boolean get() = actions.isEmpty()

    /** "12 taps" or "1 gesture", for the cards and the messages. */
    val summary: String get() = "${actions.size} ${mode.label(actions.size)}"
}

/** A raw touch sample, as collected from the Android views or from the accessibility service overlay. */
data class TouchSample(
    val pointerId: Int,
    val action: TouchAction,
    val x: Float,
    val y: Float,
    val eventTimeMs: Long,
)

enum class TouchAction {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}
