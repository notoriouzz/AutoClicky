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

/** One stroke of the gesture sent back to the app: the path a finger follows, at the time it must start. */
data class RelayStrokeSpec(
    val points: List<RecordedPoint>,
    val startTimeMs: Long,
    val durationMs: Long,
)

/**
 * Computes what the app must receive after the user performed a gesture on the relay overlay.
 *
 * The relay intercepts the touches, so the app only sees what is sent back through
 * [android.accessibilityservice.AccessibilityService.dispatchGesture]. The recorded stroke is therefore replayed as
 * close as possible to what the user did: same fingers, same path, same timing.
 *
 * A press that doesn't move is shortened ([MAX_STILL_PRESS_MS]): the overlay cannot capture anything while the gesture
 * is being sent, so a long press must not leave the app without its touches for longer than an app needs to trigger its
 * own long click.
 */
object RelayGesturePlanner {

    /** Same maximum as the engine: an Android gesture can only hold 5 pointers. */
    const val MAX_FINGER_COUNT = 5

    /** Android rejects a gesture longer than 59 999 ms. */
    const val MAX_GESTURE_DURATION_MS = 59_000L

    /** Longest press sent back for a finger that didn't move. */
    const val MAX_STILL_PRESS_MS = 900L

    /** Shortest stroke: the framework ignores a stroke that lasts 0 ms. */
    const val MIN_STROKE_DURATION_MS = 1L

    /** A finger that moved less than this is considered still: it pressed, it didn't swipe. */
    private const val STILL_DISTANCE_PX = 8f

    /**
     * The strokes to dispatch for a recorded gesture.
     *
     * @return one stroke per finger, ordered by start time, or an empty list when nothing can be sent back.
     */
    fun plan(stroke: RecordedStroke): List<RelayStrokeSpec> =
        stroke.fingers
            .filter { finger -> finger.points.size >= 2 }
            .map { finger ->
                RelayStrokeSpec(
                    points = finger.points,
                    startTimeMs = (finger.startTimeMs - stroke.startTimeMs).coerceAtLeast(0L),
                    durationMs = finger.replayDurationMs(),
                )
            }
            .filter { spec -> spec.startTimeMs < MAX_GESTURE_DURATION_MS }
            .sortedBy { spec -> spec.startTimeMs }
            .take(MAX_FINGER_COUNT)
            .map { spec ->
                // The whole gesture must fit in the framework limit: the last fingers of a very long press are cut.
                val duration = spec.durationMs.coerceAtMost(MAX_GESTURE_DURATION_MS - spec.startTimeMs)
                spec.copy(durationMs = duration.coerceAtLeast(MIN_STROKE_DURATION_MS))
            }
            .filter { spec -> spec.durationMs >= MIN_STROKE_DURATION_MS }

    private fun RecordedFinger.replayDurationMs(): Long {
        val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(MIN_STROKE_DURATION_MS)

        return if (maxDistanceFromStartPx <= STILL_DISTANCE_PX) {
            durationMs.coerceAtMost(MAX_STILL_PRESS_MS)
        } else {
            durationMs
        }.coerceAtMost(MAX_GESTURE_DURATION_MS)
    }
}
