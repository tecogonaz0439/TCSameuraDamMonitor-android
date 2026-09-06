// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

import android.graphics.drawable.Drawable


data class AppLinkInfo(
    val packageName: String,
    val activityName: String,
    val name: String,
    val icon: Drawable,
)
