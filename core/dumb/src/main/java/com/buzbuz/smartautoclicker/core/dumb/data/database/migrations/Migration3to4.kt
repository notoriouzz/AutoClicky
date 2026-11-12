/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.dumb.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.buzbuz.smartautoclicker.core.dumb.data.database.DUMB_SCENARIO_TABLE

/**
 * Adds the pin state and the creation date of a recording.
 *
 * Existing recordings are unpinned, and their creation date is their database id: the id is an autoincrement value that
 * never changes on rename or playback, so it keeps the original creation order even if the exact timestamp is unknown.
 * New recordings store the real epoch time, which is always larger than the largest realistic id.
 */
object Migration3to4 : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE $DUMB_SCENARIO_TABLE ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE $DUMB_SCENARIO_TABLE ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE $DUMB_SCENARIO_TABLE SET created_at = id WHERE created_at = 0")
    }
}
