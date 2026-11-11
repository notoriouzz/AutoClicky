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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioRecorderTest {

    @Test
    fun shortStillStrokeIsATap() {
        val stroke = RecordedStroke(
            fingers = listOf(
                RecordedFinger(
                    points = listOf(
                        RecordedPoint(100f, 200f, 0),
                        RecordedPoint(101f, 201f, 80),
                    )
                )
            )
        )

        val actions = ScenarioRecorder.toRecordedActions(listOf(stroke))

        assertEquals(1, actions.size)
        val tap = actions.first() as RecordedAction.Tap
        assertEquals(100f, tap.x, 0.01f)
        assertEquals(200f, tap.y, 0.01f)
        assertEquals(80L, tap.durationMs)
    }

    @Test
    fun movingFingerIsAGesture() {
        val stroke = RecordedStroke(
            fingers = listOf(
                RecordedFinger(
                    points = listOf(
                        RecordedPoint(100f, 200f, 0),
                        RecordedPoint(400f, 200f, 300),
                    )
                )
            )
        )

        val actions = ScenarioRecorder.toRecordedActions(listOf(stroke))

        assertEquals(1, actions.size)
        assertTrue(actions.first() is RecordedAction.Gesture)
    }

    @Test
    fun longPressIsAGesture() {
        val stroke = RecordedStroke(
            fingers = listOf(
                RecordedFinger(
                    points = listOf(
                        RecordedPoint(100f, 200f, 0),
                        RecordedPoint(100f, 200f, 2_000),
                    )
                )
            )
        )

        val actions = ScenarioRecorder.toRecordedActions(listOf(stroke))

        assertTrue(actions.first() is RecordedAction.Gesture)
    }

    @Test
    fun actionsAreOrderedByTime() {
        val late = RecordedStroke(
            fingers = listOf(
                RecordedFinger(
                    points = listOf(
                        RecordedPoint(10f, 10f, 5_000),
                        RecordedPoint(10f, 10f, 5_050),
                    )
                )
            )
        )
        val early = RecordedStroke(
            fingers = listOf(
                RecordedFinger(
                    points = listOf(
                        RecordedPoint(20f, 20f, 1_000),
                        RecordedPoint(20f, 20f, 1_050),
                    )
                )
            )
        )

        val actions = ScenarioRecorder.toRecordedActions(listOf(late, early))

        assertEquals(1_000L, actions.first().startTimeMs)
        assertEquals(5_000L, actions.last().startTimeMs)
    }
}
