// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * ダムデータの読み込み（手動更新、自動更新、初回読込）状態を表す列挙型。
 */
enum class DamLoadStatus {
    /** 初期状態（ロード未開始） */
    INITIAL,
    /** ロード成功（キャッシュまたは通信データの読み込み完了） */
    SUCCESS,
    /** ネットワークエラー（通信不可状態） */
    NETWORK_UNAVAILABLE,
    /** ロード失敗（その他のエラーや例外発生） */
    LOADING_FAILURE
}
