// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.testutil

import arrow.core.Either

fun <L, R> Either<L, R>.shouldBeRight(): R =
    fold(
        ifLeft = { throw AssertionError("Expected Right, but was Left($it).") },
        ifRight = { it }
    )

fun <L, R> Either<L, R>.shouldBeLeft(): L =
    fold(
        ifLeft = { it },
        ifRight = { throw AssertionError("Expected Left, but was Right($it).") }
    )
