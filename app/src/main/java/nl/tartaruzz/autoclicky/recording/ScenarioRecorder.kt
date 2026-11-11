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

/**
 * Turns collected [RecordedStroke]s into the action list of a template.
 *
 * A stroke where a single finger stayed still and short enough is a tap, everything else is a gesture. This keeps the
 * saved template as close as possible to what the user did, and lets the engine replay swipes and multi-finger
 * gestures natively.
 */
object ScenarioRecorder {

    /** Maximum duration of a press to be considered a tap. */
    const val TAP_MAX_DURATION_MS = 500L

    /** Maximum distance a finger can move to still be considered a tap. */
    const val TAP_MAX_DISTANCE_PX = 32f

    fun toRecordedActions(strokes: List<RecordedStroke>): List<RecordedAction> =
        strokes
            .filter { stroke -> stroke.fingers.isNotEmpty() && stroke.fingers.all { it.points.size >= 2 } }
            .map { stroke -> stroke.toRecordedAction() }
            .sortedBy { action -> action.startTimeMs }

    fun tapsToRecordedActions(taps: List<RecordedAction.Tap>): List<RecordedAction> =
        taps.sortedBy { tap -> tap.startTimeMs }

    private fun RecordedStroke.toRecordedAction(): RecordedAction {
        val singleFinger = fingers.singleOrNull()

        return if (singleFinger != null && isTap(singleFinger)) {
            RecordedAction.Tap(
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                x = singleFinger.startPoint.x,
                y = singleFinger.startPoint.y,
            )
        } else {
            RecordedAction.Gesture(
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                fingers = fingers.take(MAX_FINGER_COUNT),
            )
        }
    }

    private fun isTap(finger: RecordedFinger): Boolean =
        finger.endTimeMs - finger.startTimeMs <= TAP_MAX_DURATION_MS &&
            finger.maxDistanceFromStartPx <= TAP_MAX_DISTANCE_PX

    /** Same maximum as the engine: an Android gesture can only hold 5 pointers. */
    private const val MAX_FINGER_COUNT = 5
}
