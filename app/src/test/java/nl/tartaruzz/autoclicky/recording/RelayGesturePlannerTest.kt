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

class RelayGesturePlannerTest {

    @Test
    fun aTapIsForwardedWhereItWasMade() {
        val stroke = strokeOf(
            listOf(
                point(x = 120f, y = 800f, timeMs = 1_000),
                point(x = 121f, y = 800f, timeMs = 1_060),
            )
        )

        val specs = RelayGesturePlanner.plan(stroke)

        assertEquals(1, specs.size)
        assertEquals(0L, specs.first().startTimeMs)
        assertEquals(60L, specs.first().durationMs)
        assertEquals(120f, specs.first().points.first().x, 0.01f)
        assertEquals(800f, specs.first().points.first().y, 0.01f)
    }

    @Test
    fun aLongPressIsShortenedToTheLongestForwardedPress() {
        val stroke = strokeOf(
            listOf(
                point(x = 400f, y = 500f, timeMs = 2_000),
                point(x = 400f, y = 500f, timeMs = 5_000),
            )
        )

        val specs = RelayGesturePlanner.plan(stroke)

        assertEquals(RelayGesturePlanner.MAX_STILL_PRESS_MS, specs.first().durationMs)
    }

    @Test
    fun aSwipeKeepsItsDurationAndItsPath() {
        val stroke = strokeOf(
            listOf(
                point(x = 100f, y = 1_500f, timeMs = 0),
                point(x = 400f, y = 1_400f, timeMs = 200),
                point(x = 900f, y = 1_300f, timeMs = 500),
            )
        )

        val specs = RelayGesturePlanner.plan(stroke)

        assertEquals(1, specs.size)
        assertEquals(500L, specs.first().durationMs)
        assertEquals(3, specs.first().points.size)
    }

    @Test
    fun everyFingerIsForwardedFromTheStartOfTheGesture() {
        val stroke = RecordedStroke(
            fingers = listOf(
                RecordedFinger(listOf(point(100f, 900f, 1_000), point(300f, 900f, 1_400))),
                RecordedFinger(listOf(point(900f, 900f, 1_200), point(700f, 900f, 1_400))),
            )
        )

        val specs = RelayGesturePlanner.plan(stroke)

        assertEquals(2, specs.size)
        assertEquals(0L, specs[0].startTimeMs)
        assertEquals(200L, specs[1].startTimeMs)
        // The second finger stops when the first one does, at the end of the gesture.
        assertEquals(200L, specs[1].durationMs)
    }

    @Test
    fun onlyFiveFingersAreForwarded() {
        val stroke = RecordedStroke(
            fingers = (0 until 7).map { fingerIndex ->
                RecordedFinger(
                    listOf(
                        point(x = 100f * fingerIndex, y = 900f, timeMs = 0),
                        point(x = 100f * fingerIndex, y = 900f, timeMs = 100),
                    )
                )
            }
        )

        assertEquals(RelayGesturePlanner.MAX_FINGER_COUNT, RelayGesturePlanner.plan(stroke).size)
    }

    @Test
    fun aStrokeWithoutEnoughPointsIsIgnored() {
        val stroke = strokeOf(listOf(point(x = 10f, y = 10f, timeMs = 0)))

        assertTrue(RelayGesturePlanner.plan(stroke).isEmpty())
    }

    @Test
    fun aGestureLongerThanTheFrameworkLimitIsCut() {
        val stroke = strokeOf(
            listOf(
                point(x = 10f, y = 10f, timeMs = 0),
                point(x = 500f, y = 1_200f, timeMs = 70_000),
            )
        )

        val specs = RelayGesturePlanner.plan(stroke)

        assertEquals(RelayGesturePlanner.MAX_GESTURE_DURATION_MS, specs.first().durationMs)
    }

    private fun strokeOf(points: List<RecordedPoint>): RecordedStroke =
        RecordedStroke(fingers = listOf(RecordedFinger(points)))

    private fun point(x: Float, y: Float, timeMs: Long): RecordedPoint =
        RecordedPoint(x = x, y = y, timeMs = timeMs)
}
