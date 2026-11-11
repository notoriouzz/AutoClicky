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
import org.junit.Test

class TapMergerTest {

    /** The calculator keypad of the device the bug was reported on: 1, +, 1, +, 1, =, AC. */
    private val keypad = listOf(
        RecordedViewClick(left = 20, top = 1900, right = 286, bottom = 1968, timeMs = 0),
        RecordedViewClick(left = 800, top = 1900, right = 1056, bottom = 1968, timeMs = 0),
    )

    @Test
    fun everyTapOfAFastSequenceIsKept() {
        // 11 taps, 75 ms apart, like the 11 taps the panel used to store as a single action.
        val clicks = (0 until 11).map { index ->
            val key = keypad[index % keypad.size]
            RecordedViewClick(
                left = key.left,
                top = key.top,
                right = key.right,
                bottom = key.bottom,
                timeMs = index * 75L,
            )
        }
        val touches = clicks.map { click -> RecordedTouch(0f, 0f, click.timeMs) }

        val taps = TapMerger.merge(touches, clicks)

        assertEquals(11, taps.size)
        assertEquals(0L, taps.first().startTimeMs)
        assertEquals(750L, taps.last().startTimeMs)
        // (0,0) touches carry no position: the centre of the clicked view is used.
        assertEquals(153f, taps[0].x, 0.01f)
        assertEquals(928f, taps[1].x, 0.01f)
    }

    @Test
    fun aGestureReportedTwiceIsASingleTap() {
        val click = RecordedViewClick(left = 20, top = 1900, right = 286, bottom = 1968, timeMs = 1_000)
        val touches = listOf(
            RecordedTouch(0f, 0f, 960L),
            RecordedTouch(0f, 0f, 965L),
        )

        val taps = TapMerger.merge(touches, listOf(click, click.copy(timeMs = 1_008)))

        assertEquals(1, taps.size)
        // The touch happened before the click: the press starts at the touch.
        assertEquals(960L, taps.first().startTimeMs)
    }

    @Test
    fun theTouchPositionWinsOverTheViewCentre() {
        val click = RecordedViewClick(left = 0, top = 0, right = 1_080, bottom = 2_340, timeMs = 500)

        val taps = TapMerger.merge(listOf(RecordedTouch(120f, 200f, 480L)), listOf(click))

        assertEquals(1, taps.size)
        assertEquals(120f, taps.first().x, 0.01f)
        assertEquals(200f, taps.first().y, 0.01f)
    }

    @Test
    fun aTapOnSomethingThatIsNotAViewIsKept() {
        val taps = TapMerger.merge(
            touches = listOf(RecordedTouch(400f, 900f, 2_000L)),
            clicks = emptyList(),
        )

        assertEquals(1, taps.size)
        assertEquals(400f, taps.first().x, 0.01f)
        assertEquals(900f, taps.first().y, 0.01f)
    }

    @Test
    fun aTouchWithoutCoordinatesAndWithoutClickIsDropped() {
        val taps = TapMerger.merge(
            touches = listOf(RecordedTouch(0f, 0f, 3_000L)),
            clicks = emptyList(),
        )

        assertEquals(0, taps.size)
    }

    @Test
    fun tapsAreOrderedByTime() {
        val clicks = listOf(
            RecordedViewClick(left = 20, top = 1900, right = 286, bottom = 1968, timeMs = 5_000),
            RecordedViewClick(left = 800, top = 1900, right = 1056, bottom = 1968, timeMs = 1_000),
        )

        val taps = TapMerger.merge(emptyList(), clicks)

        assertEquals(1_000L, taps.first().startTimeMs)
        assertEquals(5_000L, taps.last().startTimeMs)
    }
}
