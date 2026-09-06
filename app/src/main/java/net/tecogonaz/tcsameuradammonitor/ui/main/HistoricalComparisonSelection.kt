// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.ui.main

/**
 * 選択年集合から1年をトグルします。
 *
 * 対象年は比較対象の全対象年（データ開始年の2002年から今年まで）で、今年も含みます。
 * 全選択中に年を解除した場合はその年だけが外れ、1年以上解除中に最後の未選択年を選択した場合は全選択へ復帰します。
 *
 * @param selected 現在の選択年集合
 * @param year トグル対象の年
 * @return トグル後の選択年集合
 */
fun togglePastYearSelection(selected: Set<Int>, year: Int): Set<Int> =
    if (year in selected) selected - year else selected + year

/**
 * 「全対象年」チップ（mode 3の「貯水率全て」/ mode 4の「貯水量全て」）のトグル遷移を返します。
 *
 * 全選択状態なら全解除（空集合）へ、未全選択（1年以上解除中）なら全選択へ遷移します。
 *
 * @param selected 現在の選択年集合
 * @param availableYears 選択可能な全対象年の昇順リスト（過去年+今年）
 * @return 遷移後の選択年集合
 */
fun toggleAllPastYears(selected: Set<Int>, availableYears: List<Int>): Set<Int> =
    if (isAllPastYearsSelected(selected, availableYears)) {
        emptySet()
    } else {
        availableYears.toSet()
    }

/**
 * 全対象年が選択されているかを判定します。
 *
 * @param selected 現在の選択年集合
 * @param availableYears 選択可能な全対象年の昇順リスト（過去年+今年）
 * @return 全対象年が選択されている場合はtrue。availableYearsが空の場合はfalse
 */
fun isAllPastYearsSelected(selected: Set<Int>, availableYears: List<Int>): Boolean =
    availableYears.isNotEmpty() && selected.containsAll(availableYears)
