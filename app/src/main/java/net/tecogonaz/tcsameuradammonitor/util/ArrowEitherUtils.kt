// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import arrow.core.Either
import kotlinx.coroutines.CancellationException

/**
 * 通常例外だけを [Either.Left] に変換し、Coroutine cancellationは再スローする。
 *
 * Repository / DataSource境界では失敗をLeftで返すが、キャンセルを握ると呼び出し元の
 * coroutine制御を壊すため、このhelperを経由して方針を統一する。
 */
internal inline fun <A> catchNonCancellation(block: () -> A): Either<Throwable, A> =
    Either.catch(block).onLeft { error ->
        if (error is CancellationException) throw error
    }

/** suspend副作用向けの [catchNonCancellation]。 */
internal suspend inline fun <A> catchNonCancellationSuspend(
    crossinline block: suspend () -> A
): Either<Throwable, A> =
    try {
        Either.Right(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Either.Left(error)
    }
