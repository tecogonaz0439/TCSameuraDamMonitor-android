// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model


/**
 * 対象ダムの静的設定（メタ情報およびURL構成）を保持するデータモデル。
 *
 * @property id 観測所ID/ダム識別子
 * @property prefecture 都道府県名
 * @property prefectureEn 都道府県名の英語表記
 * @property waterSystem 水系名
 * @property waterSystemEn 水系名の英語表記
 * @property river 河川名
 * @property riverEn 河川名の英語表記
 * @property nameJa ダム名称（日本語）
 * @property nameEn ダム名称（英語）
 * @property dataUrl 国土交通省（MLIT）のダムリアルタイムデータ取得先URL
 * @property disasterInfoUrl 防災情報URL
 * @property otherUrls その他の関連外部リンク一覧
 * @property geoUrl ダムの位置情報地図URL
 */
data class DamConfig(
    val id: String = AppSettings.DEFAULT_DAM_ID,
    val prefecture: String = DEFAULT.prefecture,
    val prefectureEn: String = DEFAULT.prefectureEn,
    val waterSystem: String = DEFAULT.waterSystem,
    val waterSystemEn: String = DEFAULT.waterSystemEn,
    val river: String = DEFAULT.river,
    val riverEn: String = DEFAULT.riverEn,
    val nameJa: String = DEFAULT.nameJa,
    val nameEn: String = DEFAULT.nameEn,
    val dataUrl: String = DEFAULT.dataUrl,
    val disasterInfoUrl: String = DEFAULT.disasterInfoUrl,
    val otherUrls: List<ExternalUrl> = DEFAULT.otherUrls,
    val geoUrl: String = DEFAULT.geoUrl
) {
    companion object {
        
        val DEFAULT: DamConfig by lazy {
            DamListData.allDams.first { it.id == AppSettings.DEFAULT_DAM_ID }
        }
    }

    
    val siteInfoUrl: String
        get() = if (dataUrl.contains("www1.river.go.jp/cgi-bin/DspDamData.exe"))
            "https://www1.river.go.jp/cgi-bin/SiteInfo.exe?ID=$id"
        else ""

    
    val searchUrl: String
        get() = if (dataUrl.contains("www1.river.go.jp/cgi-bin/DspDamData.exe"))
            "https://www1.river.go.jp/cgi-bin/SrchDamData.exe?ID=$id&KIND=1&PAGE=0"
        else ""

    
    fun historicalSearchUrl(startDate: String, endDate: String): String =
        if (dataUrl.contains("www1.river.go.jp/cgi-bin/DspDamData.exe"))
            "https://www1.river.go.jp/cgi-bin/DspDamData.exe?KIND=1&ID=$id&BGNDATE=$startDate&ENDDATE=$endDate&KAWABOU=NO"
        else ""
}

/**
 * 与えられたダムIDに一致する[DamConfig]を検索して取得します。
 *
 * 見つからない場合はデフォルトのダムID（早明浦ダム）の設定、それも取得できない場合は静的デフォルトのDamConfigを返します。
 *
 * @param targetDamId 対象ダムのID（デフォルト: [AppSettings.DEFAULT_DAM_ID]）
 * @return 合致する[DamConfig]インスタンス
 */
fun getDamConfig(targetDamId: String = AppSettings.DEFAULT_DAM_ID): DamConfig {
    return DamListData.allDams.find { it.id == targetDamId }
        ?: DamListData.allDams.find { it.id == AppSettings.DEFAULT_DAM_ID }
        ?: DamConfig.DEFAULT
}
