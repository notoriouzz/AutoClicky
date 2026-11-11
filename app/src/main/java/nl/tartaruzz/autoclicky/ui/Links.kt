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

package nl.tartaruzz.autoclicky.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Where the people who want to support the project can buy a coffee. */
const val SUPPORT_URL = "https://ko-fi.com/tartaruzz"

/** Text of the coffee button, shared by the main screen and the About dialog. */
const val SUPPORT_LABEL = "☕ Buy me a coffee"

/**
 * Open [url] in the browser.
 *
 * A device without any browser (or with the intent disabled) must not crash the app: the failure is only reported with a
 * toast.
 */
fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(this, "Cannot open $url", Toast.LENGTH_SHORT).show()
    }
}
