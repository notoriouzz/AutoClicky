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

class StrokeCollectorTest {

    private val collector = StrokeCollector()

    @Test
    fun singleTapProducesOneStrokeWithOneFinger() {
        collector.onSample(down(pointerId = 0, x = 100f, y = 200f, timeMs = 0))
        collector.onSample(up(pointerId = 0, x = 100f, y = 200f, timeMs = 50))

        val strokes = collector.getStrokes()

        assertEquals(1, strokes.size)
        assertEquals(1, strokes.first().fingers.size)
        assertEquals(2, strokes.first().fingers.first().points.size)
        assertEquals(0L, strokes.first().startTimeMs)
        assertEquals(50L, strokes.first().endTimeMs)
    }

    @Test
    fun twoSequentialTapsProduceTwoStrokes() {
        collector.onSample(down(0, 10f, 10f, 0))
        collector.onSample(up(0, 10f, 10f, 40))
        collector.onSample(down(0, 30f, 30f, 1_000))
        collector.onSample(up(0, 30f, 30f, 1_040))

        val strokes = collector.getStrokes()

        assertEquals(2, strokes.size)
        assertEquals(1_000L, strokes[1].startTimeMs)
    }

    @Test
    fun twoOverlappingPointersProduceASingleStrokeWithTwoFingers() {
        collector.onSample(down(0, 100f, 100f, 0))
        collector.onSample(down(1, 300f, 300f, 20))
        collector.onSample(move(0, 120f, 100f, 100))
        collector.onSample(up(0, 120f, 100f, 200))
        collector.onSample(up(1, 300f, 300f, 400))

        val strokes = collector.getStrokes()

        assertEquals(1, strokes.size)
        assertEquals(2, strokes.first().fingers.size)
        assertEquals(400L, strokes.first().endTimeMs)
    }

    @Test
    fun inProgressStrokeIsReported() {
        collector.onSample(down(0, 5f, 5f, 0))

        assertTrue(collector.isCollecting)
        assertEquals(1, collector.getStrokes().size)
    }

    private fun down(pointerId: Int, x: Float, y: Float, timeMs: Long) =
        TouchSample(pointerId, TouchAction.DOWN, x, y, timeMs)

    private fun move(pointerId: Int, x: Float, y: Float, timeMs: Long) =
        TouchSample(pointerId, TouchAction.MOVE, x, y, timeMs)

    private fun up(pointerId: Int, x: Float, y: Float, timeMs: Long) =
        TouchSample(pointerId, TouchAction.UP, x, y, timeMs)
}
