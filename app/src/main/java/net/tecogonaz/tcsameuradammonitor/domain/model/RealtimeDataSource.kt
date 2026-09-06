// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

enum class RealtimeDataSource {
    MLIT_DIRECT,
    SUDMONITOR
}

const val SUDMONITOR_BASE_URL = "https://sudmonitor.kusugami-lab.net"

const val SUDMONITOR_HOST = "sudmonitor.kusugami-lab.net"

val SUDMONITOR_SUPPORTED_DAM_IDS: Set<String> = setOf(AppSettings.DEFAULT_DAM_ID)
