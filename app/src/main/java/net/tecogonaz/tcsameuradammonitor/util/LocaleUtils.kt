// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.util

import android.content.Context
import net.tecogonaz.tcsameuradammonitor.domain.model.DamConfig
import java.util.Locale


/**
 * 端末ロケール（言語タグ）および日本語/英語のローカライズ表示に関する文字列処理ユーティリティ。
 *
 * ダム名や河川名、水系名などを端末の設定に追従してローカライズ表示（英語/日本語）する各種ヘルパーを提供します。
 */
object LocaleUtils {
    
    /**
     * アプリケーションが現在動作している実質的なロケールを取得します。
     *
     * @param context コンテキスト
     * @return 端末のアクティブなロケール
     */
    fun effectiveLocale(context: Context): Locale =
        context.resources.configuration.locales[0] ?: Locale.getDefault()

    /**
     * 言語タグ文字列からロケールオブジェクトを生成します。
     *
     * @param localeTag ロケールを示すタグ文字列（例: "ja", "en"）
     * @return [Locale]オブジェクト
     */
    fun localeForTag(localeTag: String): Locale =
        Locale.forLanguageTag(localeTag).takeUnless { it.language.isEmpty() } ?: Locale.getDefault()

    /**
     * 現在の端末ロケールが日本語であるかどうかを判定します。
     */
    fun isJapanese(context: Context): Boolean = effectiveLocale(context).language == Locale.JAPANESE.language

    /**
     * 指定された言語タグが日本語であるかどうかを判定します。
     */
    fun isJapanese(localeTag: String): Boolean = localeForTag(localeTag).language == Locale.JAPANESE.language

    
    fun normalDamName(context: Context, damConfig: DamConfig): String =
        normalLocalizedName(isJapanese(context), damConfig.nameJa, damConfig.nameEn)

    
    fun normalDamName(localeTag: String, damConfig: DamConfig): String =
        normalLocalizedName(isJapanese(localeTag), damConfig.nameJa, damConfig.nameEn)

    
    fun shortDamName(context: Context, damConfig: DamConfig): String =
        shortLocalizedName(isJapanese(context), damConfig.nameJa, damConfig.nameEn)

    
    fun shortDamName(localeTag: String, damConfig: DamConfig): String =
        shortLocalizedName(isJapanese(localeTag), damConfig.nameJa, damConfig.nameEn)

    
    fun normalWaterSystemName(context: Context, damConfig: DamConfig): String =
        normalLocalizedName(isJapanese(context), damConfig.waterSystem, damConfig.waterSystemEn)

    
    fun normalWaterSystemName(localeTag: String, damConfig: DamConfig): String =
        normalLocalizedName(isJapanese(localeTag), damConfig.waterSystem, damConfig.waterSystemEn)

    
    fun shortWaterSystemName(context: Context, damConfig: DamConfig): String =
        shortLocalizedName(isJapanese(context), damConfig.waterSystem, damConfig.waterSystemEn)

    
    fun shortWaterSystemName(localeTag: String, damConfig: DamConfig): String =
        shortLocalizedName(isJapanese(localeTag), damConfig.waterSystem, damConfig.waterSystemEn)

    
    fun normalRiverName(context: Context, damConfig: DamConfig): String =
        normalLocalizedName(isJapanese(context), damConfig.river, damConfig.riverEn)

    
    fun normalRiverName(localeTag: String, damConfig: DamConfig): String =
        normalLocalizedName(isJapanese(localeTag), damConfig.river, damConfig.riverEn)

    
    fun shortRiverName(context: Context, damConfig: DamConfig): String =
        shortLocalizedName(isJapanese(context), damConfig.river, damConfig.riverEn)

    
    fun shortRiverName(localeTag: String, damConfig: DamConfig): String =
        shortLocalizedName(isJapanese(localeTag), damConfig.river, damConfig.riverEn)


    private fun normalLocalizedName(isJapanese: Boolean, nameJa: String, nameEn: String): String =
        if (isJapanese) {
            nameJa.ifBlank { nameEn }
        } else {
            listOf(nameEn, nameJa)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        }

    private fun shortLocalizedName(isJapanese: Boolean, nameJa: String, nameEn: String): String =
        if (isJapanese) nameJa.ifBlank { nameEn } else nameEn.ifBlank { nameJa }
}
