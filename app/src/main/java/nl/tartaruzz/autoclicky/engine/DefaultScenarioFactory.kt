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

package nl.tartaruzz.autoclicky.engine

import android.graphics.Point

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DUMB_SCENARIO_MAX_DURATION_MINUTES
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario

/**
 * Builds the scenarios used by AutoClicky.
 *
 * The vendored engine only stores domain objects, so every new scenario is created with temporary identifiers
 * ([Identifier.asTemporary]): the database replaces them with its own ids on insertion.
 */
object DefaultScenarioFactory {

    const val QUICK_CLICKS_SCENARIO_NAME = "Quick clicks"

    /** Duration of a single click, in milliseconds. */
    const val DEFAULT_PRESS_DURATION_MS = 50L

    /** Delay between two clicks, in milliseconds. */
    const val DEFAULT_REPEAT_DELAY_MS = 1_000L

    /**
     * Create a scenario repeating a single click on [position] every [repeatDelayMs], until the user stops it.
     */
    fun createSingleClickScenario(
        position: Point,
        name: String = QUICK_CLICKS_SCENARIO_NAME,
        repeatDelayMs: Long = DEFAULT_REPEAT_DELAY_MS,
        pressDurationMs: Long = DEFAULT_PRESS_DURATION_MS,
    ): DumbScenario {
        val scenarioId = Identifier(TEMP_SCENARIO_ID, asTemporary = true)

        return DumbScenario(
            id = scenarioId,
            name = name,
            dumbActions = listOf(
                DumbAction.DumbClick(
                    id = Identifier(TEMP_ACTION_ID, asTemporary = true),
                    scenarioId = scenarioId,
                    name = "Click",
                    priority = 0,
                    repeatCount = 1,
                    isRepeatInfinite = true,
                    repeatDelayMs = repeatDelayMs,
                    position = position,
                    pressDurationMs = pressDurationMs,
                )
            ),
            repeatCount = 1,
            isRepeatInfinite = true,
            maxDurationMin = DUMB_SCENARIO_MAX_DURATION_MINUTES,
            isDurationInfinite = true,
            randomize = false,
        )
    }

    private const val TEMP_SCENARIO_ID = 1L
    private const val TEMP_ACTION_ID = 1L
}
