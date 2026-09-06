// SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
// SPDX-License-Identifier: Apache-2.0

package net.tecogonaz.tcsameuradammonitor.domain.model

data class OssInfo(
    val name: String,
    val url: String,
    val copyright: String,
    val licenseText: String
)

object AppInfoTexts {
    val LICENSE = """
Copyright (C) 2026 tecogonaz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

    """.trimIndent()

    val OSS_LIST = listOf(
        OssInfo(
            name = "AndroidX / Jetpack Libraries",
            url = "https://android.googlesource.com/platform/frameworks/support",
            copyright = """
Copyright (C) The Android Open Source Project
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Arrow",
            url = "https://github.com/arrow-kt/arrow",
            copyright = """
Copyright (C) The Arrow Authors
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Guava",
            url = "https://github.com/google/guava",
            copyright = """
Copyright (C) Google Inc.
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Hilt / Dagger",
            url = "https://github.com/google/dagger",
            copyright = """
Copyright (C) The Dagger Authors
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "JSR-305",
            url = "https://github.com/amaembo/jsr-305",
            copyright = """
Copyright (c) JSR-305 contributors
            """.trimIndent(),
            licenseText = """
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the copyright notice, conditions
and disclaimer are retained.

THIS SOFTWARE IS PROVIDED "AS IS" WITHOUT WARRANTIES OF ANY KIND.
            """.trimIndent()
        ),
        OssInfo(
            name = "JSpecify",
            url = "https://github.com/jspecify/jspecify",
            copyright = """
Copyright (C) The JSpecify Authors
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Jakarta Dependency Injection",
            url = "https://github.com/jakartaee/inject",
            copyright = """
Copyright (c) Eclipse Foundation
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Kotlin / Kotlinx Libraries",
            url = "https://github.com/JetBrains/kotlin",
            copyright = """
Copyright 2010-2026 JetBrains s.r.o. and Kotlin contributors
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Ktor",
            url = "https://github.com/ktorio/ktor",
            copyright = """
Copyright 2014-2026 JetBrains s.r.o. and Ktor contributors
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "Okio",
            url = "https://github.com/square/okio",
            copyright = """
Copyright 2013 Square, Inc.
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
        OssInfo(
            name = "SLF4J",
            url = "https://github.com/qos-ch/slf4j",
            copyright = """
Copyright (c) QOS.ch
            """.trimIndent(),
            licenseText = """
Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the "Software"),
to deal in the Software without restriction.

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
            """.trimIndent()
        ),
        OssInfo(
            name = "javax.inject",
            url = "https://github.com/javax-inject/javax-inject",
            copyright = """
Copyright (C) The JSR-330 Expert Group
            """.trimIndent(),
            licenseText = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            """.trimIndent()
        ),
    )

    val PRIVACY_POLICY = """
**Last updated: September 5, 2026**

> This English version is a translation of the Japanese original version. The Japanese original is the authoritative text. If there is any discrepancy or conflict between this English translation and the Japanese original, the Japanese original prevails.

## Scope and Information Not Obtained

This Privacy Policy applies to the Android version of **Sameura Dam Monitor** (Japanese name: 早明浦ダム 貯水率モニタ, the "App") officially distributed by the developer. The App retrieves and displays dam data published through the Water Information System of the Ministry of Land, Infrastructure, Transport and Tourism of Japan ("MLIT").

The App does not require an account or ask users to enter a name, email address, phone number, or similar information. It does not obtain location through device location APIs or device identifiers such as an IMEI or advertising ID. It does not include SDKs for advertising, usage analytics or tracking, or crash reporting operated by this project.

The App does not automatically send retrieved dam data, user preferences, or debug logs to the developer. See Network Communications below for information processed when dam data is retrieved.

## Network Communications

To retrieve dam data, the App communicates over HTTPS with either of the following destinations. The dam data originates from MLIT's Water Information System.

- Cache server operated by the project developer: https://sudmonitor.kusugami-lab.net
- MLIT origin server: https://www1.river.go.jp

The cache server is the default destination for both real-time and historical data. Users can change each destination in the App settings to retrieve data directly from MLIT. Historical-data retrieval may communicate directly with the MLIT origin server when necessary.

When the App communicates over HTTPS with either destination, the destination may process standard connection and request information. This may include the source IP address, request time, the requested dam and period, and the type and version of the App or other networking software.

For the cache server, Cloudflare processes this information to provide and secure the service, prevent abuse, and investigate failures. The project developer does not use this information for advertising, usage analytics, or tracking.

The App does not send names, email addresses, advertising IDs, device identifiers, location obtained through device location APIs, user preferences, stored dam data, or debug logs to either destination.

Logs and security analytics provided by Cloudflare may contain standard connection and request information and, under the current configuration, are retained for no longer than seven days. The project developer does not export this information to another service for long-term retention. Cloudflare's handling of information is governed by the [Cloudflare Privacy Policy](https://www.cloudflare.com/policies/privacy/).

## Data Stored on the Device

The App stores retrieved real-time and historical data, user preferences, and debug logs used for troubleshooting in its application storage. It does not automatically upload this data to a developer-operated server or provide its own cloud synchronization. If a user shares or exports data, the data is provided to the selected destination or external app.

User preferences may be included in backup or device-transfer functions provided by Android. Backups, copies created through device transfer, and data shared or exported by the user are governed by the privacy policy of the applicable service or external app and may not be deletable from within the App.

Saved historical-search results and debug logs can be deleted using the App's deletion features. To delete all local App data, including daily historical data, clear the App's storage from the Android app information screen or uninstall the App. These actions delete all App data, including preferences.

## Changes to This Policy

This Privacy Policy may be updated to reflect changes to the App, the services it uses, or applicable laws and regulations. The updated policy and its last-updated date will be published in the source code repository.

## Contact

For questions about this Privacy Policy, please open an issue in the source code repository on Codeberg. Issues are public, so do not include your name, email address, or any other personal information.

- Codeberg: https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android

    """.trimIndent()

    val PRIVACY_POLICY_JA = """
**最終更新日: 2026-09-05**

## 適用範囲と取得しない情報

本ポリシーは、開発者が公式に配布する Android 版 **早明浦ダム 貯水率モニタ**（以下「本アプリ」）に適用されます。本アプリは、国土交通省の水文水質データベースで公開されるダム諸量データを取得して表示します。

本アプリはアカウント登録や氏名、メールアドレス、電話番号等の入力を求めません。端末の位置情報 API から得る位置情報、IMEI や広告 ID 等の端末識別子も取得しません。広告、利用状況の分析・追跡を行う SDK や、本プロジェクト独自のクラッシュ報告 SDK は組み込んでいません。

本アプリは、取得したダム諸量データ、ユーザー設定およびデバッグログを開発者へ自動送信しません。ただし、ダム諸量データの取得に伴って処理される通信情報については、次節を参照してください。

## ネットワーク通信

本アプリはダム諸量データを取得するため、次の取得先と HTTPS で通信します。データの出所は国土交通省の水文水質データベースです。

- 本プロジェクト開発者が運営するキャッシュサーバ: https://sudmonitor.kusugami-lab.net
- 国土交通省の配信元サーバ: https://www1.river.go.jp

リアルタイムデータと過去データの既定の取得先はキャッシュサーバです。利用者は設定で、それぞれの取得先を国土交通省からの直接取得へ変更できます。過去データの取得では、必要に応じて国土交通省の配信元サーバと直接通信する場合があります。

いずれの取得先との HTTPS 通信でも、送信先は、送信元 IP アドレス、リクエスト日時、要求の対象となるダムや期間、アプリその他の通信ソフトウェアの種類・バージョン等の標準的な通信情報を処理する場合があります。

キャッシュサーバでは、Cloudflare がこれらの情報をサービス提供、安全確保、不正利用防止および障害調査のために処理します。本プロジェクト開発者は、広告、利用状況の分析または追跡に使用しません。

いずれの取得先にも、氏名、メールアドレス、広告 ID、端末識別子、端末の位置情報 API から取得した位置情報、ユーザー設定、保存済みのダム諸量データまたはデバッグログを送信することはありません。

Cloudflare が提供するログやセキュリティ分析には標準的な通信情報が含まれる場合があり、現在の設定では最長7日間保持されます。本プロジェクト開発者は、これらの情報を別のサービスへ書き出して長期保存しません。Cloudflare による情報の取扱いには [Cloudflare Privacy Policy](https://www.cloudflare.com/policies/privacy/) が適用されます。

## 端末内のデータ

本アプリは、取得したリアルタイムデータと過去データ、ユーザー設定、およびトラブルシューティング用のデバッグログをアプリの保存領域に保存します。これらのデータを開発者のサーバへ自動的にアップロードしたり、独自にクラウド同期したりすることはありません。利用者が共有または書き出しを行った場合は、選択した保存先や外部アプリへデータが渡されます。

ユーザー設定は、Android が提供するバックアップまたは端末移行の対象となる場合があります。バックアップ、端末移行後のコピー、および利用者が共有または書き出したデータには、各サービスまたは外部アプリのプライバシーポリシーが適用され、本アプリから削除できない場合があります。

保存済みの過去データ検索結果とデバッグログは、アプリ内の削除機能で削除できます。日次過去データを含むすべてのローカルデータを削除するには、Android のアプリ情報画面から本アプリのストレージを消去するか、本アプリをアンインストールしてください。これらの操作では、設定を含む本アプリのデータがすべて削除されます。

## 本ポリシーの変更

本ポリシーは、本アプリ、利用サービスまたは法令等の変更に応じて更新する場合があります。変更内容と最終更新日は、ソースコードリポジトリに掲載します。

## お問い合わせ

本プライバシーポリシーに関するご質問は、ソースコードリポジトリ（Codeberg）の Issue にてお問い合わせください。Issue の内容は公開されるため、氏名、メールアドレスその他の個人情報を記載しないでください。

- Codeberg: https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android

    """.trimIndent()

    val TERMS_OF_USE = """
**Last updated: September 5, 2026**

> This English version is a translation of the Japanese original version. The Japanese original is the authoritative text. If there is any discrepancy or conflict between this English translation and the Japanese original, the Japanese original prevails.

## 1. Scope

These Terms of Use ("Terms") set out the conditions for using **Sameura Dam Monitor** (Japanese name: 早明浦ダム 貯水率モニタ, the "App").

By installing, launching, using, or obtaining the App from an official distribution source, you are deemed to have agreed to these Terms. If you do not agree to these Terms, do not use the App.

These Terms define the conditions for using the App. They do not restrict the rights to use, reproduce, modify, distribute, or otherwise exercise rights in the source code, object code, derivative works, or redistributions granted under the Apache License, Version 2.0. Those materials are governed by **License** and **OSS Licenses**.

## 2. App Description

The App retrieves dam data published through the Water Information System of the Ministry of Land, Infrastructure, Transport and Tourism of Japan ("MLIT") and displays the water storage rate and related data for 125 dams across Japan, including Sameura Dam.

The App is not an official app provided, approved, sponsored, or guaranteed by MLIT or any other government agency or third party. The App's display, name, icon, description, screenshots, and similar materials do not indicate any official affiliation with those agencies or services.

The App is not an official source of information for disaster response, disaster prevention decisions, evacuation decisions, water intake or water use decisions, important business decisions, or decisions involving life, body, or property. When making important decisions, check official information from MLIT, local governments, the Japan Meteorological Agency, river administrators, news organizations, and other official sources.

## 3. Data Sources and Usage Notes

The App retrieves and processes for display dam data published through MLIT's Water Information System.

The App does not guarantee the accuracy, completeness, freshness, continuous provision, availability, or fitness for a particular purpose of the original data. Displayed content may not be current or accurate due to delays in original data updates, missing data, abnormal values, specification changes, publication suspension, communication failures, device conditions, Android power-saving controls, parsing failures, or similar causes.

Displayed values, notifications, widgets, historical graphs, day-over-day changes, week-over-week changes, messages, and similar information in the App are for reference only. You use the App at your own responsibility.

## 4. Distribution Sources and APK Distribution

The official distribution sources for the App are the Releases pages of the development repositories, **Codeberg** (primary) and **GitHub** (mirror). Built `.apk` files are distributed.

- Codeberg Releases: https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android/releases
- GitHub Releases: https://github.com/tecogonaz0439/TCSameuraDamMonitor-android/releases

The distribution of the App on app stores such as F-Droid or Google Play will be conducted at a later date, and it is not currently distributed on general app stores.

If you obtain the App from app stores such as F-Droid or Google Play in the future, the terms of use, policies, and other applicable conditions of each store govern matters relating to acquisition, distribution, updates, listing, suspension, and similar procedures. If these Terms conflict with the store's terms regarding store procedures or functions, the store's terms prevail for that scope.

The App is distributed as an executable APK file through the source code repositories above. If you obtain and install an APK from outside a general app store, the store's mechanisms for distribution, updates, review, signing, security checks, and similar functions do not apply. You are responsible for checking the source, signature, authenticity of the file, device settings (allowing installation from unknown sources), installability, and risks associated with installation.

We do not guarantee the safety, completeness, authenticity, operation, updates, or support of APKs obtained from sources other than official distribution sources, APKs modified or rebuilt by third parties, derivative versions, mirror distributions, or distributions through unofficial stores.

The official distribution version of the App currently does not provide paid sales, in-app purchases, advertisements, or subscriptions.

## 5. Open Source License

The source code of the App is published under the Apache License, Version 2.0. You may use, reproduce, modify, and distribute the source code and object code in accordance with that license.

The App uses multiple open source libraries under their respective licenses. Major runtime dependencies included in the Android version are described in **OSS Licenses**.

If you distribute a modified or derivative version using the App's name, icon, screens, description, repository information, developer name, attribution notices, or similar materials, do not mislead users into believing that it is an official version, approved version, or sponsored version of MLIT or any other third party. If you distribute a modified version, comply with the Apache License, Version 2.0, applicable laws, the terms of the distribution store, and third-party rights.

If you submit Issues, Pull Requests, patches, translations, documentation, ideas, or other contributions to the source code repository, those contributions may be incorporated into the App under the Apache License, Version 2.0, unless expressly stated otherwise at the time of submission.

## 6. Privacy and On-Device Data

The handling of data in the App is governed by the separate Privacy Policy.

- Privacy Policy: see **Privacy Policy**

The App does not require registration of a name, email address, or similar information, and it does not use device location APIs, device identifiers, advertising SDKs, usage-analytics SDKs, or a developer-operated crash-reporting SDK. It also does not automatically upload retrieved dam data, user preferences, or debug logs.

The dam data originates from MLIT's Water Information System. The default network destination for both real-time and historical data is the cache server operated by the project developer (https://sudmonitor.kusugami-lab.net). The cache server provides real-time, daily historical, and monthly data used for historical searches. You can select direct retrieval from the MLIT origin server (https://www1.river.go.jp) independently for each data source, and historical searches may access that server directly when necessary.

When the App communicates with the cache server, Cloudflare processes request metadata such as the source IP address, destination, request time, and User-Agent for service delivery, security, abuse prevention, and incident investigation. See the Privacy Policy for details and retention periods.

The App stores dam data retrieved from MLIT (real-time data and historical data), user settings, debug logs, and similar data in its application storage. User settings may leave the device through Android backup or device migration. Other stored data may leave the device through sharing features in the App, screenshots, manual copying, sharing to external apps, or other user actions or OS functions. Data other than user settings, including debug logs, is excluded from Android backup and device migration.

Saved historical search results and debug logs can be deleted using their respective in-app deletion features. Automatically retrieved daily historical data is not covered by those deletion features; to remove it completely, clear the App's storage from the Android app information screen or uninstall the App. These operations also delete all other App data. However, OS backups, copies created through device migration, files shared or copied by you, and data passed to external apps may not be deletable from within the App.

## 7. Permissions, Notifications, and Background Updates

The App uses Android permissions to retrieve dam data, check network status, display notifications, and resume automatic updates after device startup. The purpose of each permission is described in the Privacy Policy.

Notifications, automatic updates, updates after device startup, and widget updates are affected by Android specifications, device manufacturer power-saving features, network conditions, user settings, notification permissions, background restrictions, and similar factors. The App does not guarantee that notifications or automatic updates will always run exactly at scheduled times.

Debug logs are recorded automatically and stored locally. Exporting or sharing them is performed by user action. You are responsible for checking the destination, save location, file contents, and whether disclosure to a third party is necessary.

## 8. External Services and Links

The App may link to, launch, or share content with external services or external apps, including MLIT-related pages, map apps, browsers, OS sharing features, Codeberg, and GitHub.

Use of external services or external apps is subject to the terms, privacy policies, and usage conditions of each provider. We are not responsible for the content, availability, accuracy, safety, changes, suspension, or results of use of external services or external apps.

## 9. User Responsibilities and Prohibited Acts

You must use the App in compliance with applicable laws, these Terms, the terms of distribution sources, Android and distribution platform terms, third-party rights, and generally accepted social norms.

You must not engage in the following acts.

- Acts that violate laws, public order and morals, third-party rights, these Terms, or the terms of distribution sources
- Acts that impose excessive load, cause failures, perform unauthorized access, or interfere with the App, MLIT public servers, the cache server, Codeberg, GitHub, external services, third-party devices, or networks
- Acts that mislead users into believing that the App or a modified version is an official version, approved version, or sponsored version of MLIT or any other third party
- Acts of using or distributing the App or a modified version for malware, unauthorized code, phishing, fraud, impersonation, false display, misleading display, or deception of users
- Acts of presenting displayed data from the App as if it were official information and causing third parties to make incorrect important decisions
- Other acts that we reasonably determine to be outside the normal scope of use of the App

## 10. Changes, Suspension, and Termination

We may change the App's features, specifications, displays, supported OS versions, supported devices, distribution methods, distribution sources, repositories, documentation, Privacy Policy, or these Terms as necessary.

We may suspend or terminate distribution, updates, provision, or support of the App due to changes in MLIT public data specifications, suspension of external services, changes in policies or terms of distribution platforms such as Codeberg/GitHub, changes in F-Droid or other store policies, security reasons, maintenance, legal requirements, or other unavoidable circumstances.

If these Terms are changed, the revised Terms take effect when published in the repository, in the App, on a distribution page, or by another method we deem appropriate. If you use the App after the change, you are deemed to have agreed to the revised Terms.

## 11. No Warranty

The App is provided free of charge on an as-is and as-available basis. We do not warrant the App's accuracy, completeness, freshness, usefulness, availability, continuity, safety, absence of errors, fitness for a particular purpose, non-infringement of third-party rights, compatibility with devices or OS versions, storage or restoration of data, or reliable execution of notifications or automatic updates.

You are responsible for handling any damage arising from use or inability to use the App, displayed data, missing data, incorrect display, update delays, notification delays, loss of on-device data, use of external services, acquisition or installation of executable files, or use of modified versions or third-party distributions.

## 12. Limitation of Liability

To the maximum extent permitted by law, we are not liable for any damage suffered by you or any third party in connection with the App.

Even if we cannot be exempt from liability under applicable law, except in cases of our willful misconduct or gross negligence, our liability is limited to ordinary and direct damages actually incurred. We are not liable for special, indirect, incidental, consequential damages, lost profits, data loss, business interruption, device failure, third-party claims, or damage caused by failure to check official information.

## 13. Use by Minors

If a minor uses the App, the minor must obtain consent from a parent or other legal representative. If a minor uses the App, the minor is deemed to have obtained consent from the legal representative.

## 14. Governing Law and Jurisdiction

The formation, validity, interpretation, performance, and disputes related to these Terms and the App are governed by the laws of Japan.

If a dispute arises between us and a user in connection with these Terms or the App, the Takamatsu District Court has exclusive jurisdiction as the court of first instance.

## 15. Contact

As a general rule, inquiries regarding these Terms, the App, the Privacy Policy, or licenses should be made through Issues in the source code repository (Codeberg).

- Codeberg: https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android

We do not guarantee individual responses, investigations, fixes, updates, or support for all inquiries.

    """.trimIndent()

    val TERMS_OF_USE_JA = """
**最終更新日: 2026-09-05**

## 1. 適用

本利用規約（以下「本規約」）は、**早明浦ダム 貯水率モニタ**(以下「本アプリ」)の利用条件を定めるものです。

利用者は、本アプリをインストール、起動、利用、または公式配布元から取得した時点で、本規約に同意したものとみなされます。本規約に同意しない場合は、本アプリを利用しないでください。

本規約は本アプリの利用条件を定めるものであり、Apache License, Version 2.0 により許諾されるソースコード、オブジェクトコード、派生物、再配布物の利用、複製、改変、配布その他の権利を制限するものではありません。これらの取扱いは、**本アプリのライセンス**、および **本アプリのOSSライセンス** に従います。

## 2. 本アプリの内容

本アプリは、国土交通省の水文水質データベースで公開されるダム諸量データを取得し、早明浦ダムを含む日本国内 125 ダムの貯水率、および関連データを閲覧するためのアプリです。

本アプリは、国土交通省を含む政府機関、またはその他の第三者が提供、承認、後援、保証する公式アプリではありません。本アプリ内の表示、アプリ名、アイコン、説明、スクリーンショット等は、これらの機関またはサービスとの公式な提携関係を示すものではありません。

本アプリは、災害対応、防災判断、避難判断、取水・利水判断、業務上の重要判断、生命・身体・財産に関わる判断のための公式情報源ではありません。重要な判断を行う場合は、国土交通省、自治体、気象庁、河川管理者、報道機関等の公式情報を確認してください。

## 3. データの出典と利用上の注意

本アプリは、国土交通省の水文水質データベースで公開されるダム諸量データを取得し、表示用に加工して作成します。

本アプリは、原データの正確性、完全性、最新性、継続提供、可用性、特定目的への適合性を保証しません。原データの更新遅延、欠測、異常値、仕様変更、公開停止、通信障害、端末状態、Androidの省電力制御、パース処理の失敗等により、表示内容が最新または正確でない場合があります。

本アプリの表示値、通知、ウィジェット、履歴グラフ、前日比、前週比、メッセージ等は参考情報です。利用者は、自己の責任で本アプリを利用するものとします。

## 4. 配布元とAPK配布

本アプリの公式配布元は、開発用リポジトリである **Codeberg**（メイン）および **GitHub**（ミラー）の Releases ページであり、ビルド済みの `.apk` ファイルが配布されます。

- Codeberg Releases: https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android/releases
- GitHub Releases: https://github.com/tecogonaz0439/TCSameuraDamMonitor-android/releases

F-Droid や Google Play 等のアプリストアでの公開は後日別途実施する予定であり、現時点では一般的なアプリストアでの配布は行われていません。

将来的に F-Droid や Google Play 等から本アプリを取得するようになる場合、各ストアの利用規約、ポリシー、その他適用される条件が、取得、配布、更新、掲載、停止等の手続きに適用されます。本規約と各ストアの条件が手続きまたは機能に関して矛盾する場合、その範囲ではストアの条件が優先されます。

本アプリは、上記ソースコードリポジトリ上でAPK形式の実行ファイルとして配布されます。一般的なアプリストア以外からAPKを入手してインストールする場合、ストアによる配布、更新、審査、署名、セキュリティ確認等の仕組みが適用されません。利用者は、入手元、署名、ファイルの真正性、端末設定（提供元不明のアプリの許可）、インストールの可否、およびインストールに伴うリスクを自ら確認してください。

公式配布元以外から入手したAPK、第三者が改変または再ビルドしたAPK、派生版、ミラー配布物、非公式ストア上の配布物について、当方はその安全性、完全性、真正性、動作、更新、サポートを保証しません。

本アプリの公式配布版は、現時点で有償販売、アプリ内課金、広告、サブスクリプションを提供しません。

## 5. オープンソースライセンス

本アプリのソースコードは、Apache License, Version 2.0 に基づき公開されています。利用者は、同ライセンスに従い、ソースコードおよびオブジェクトコードを利用、複製、改変、配布することができます。

本アプリは、複数のオープンソースライブラリをそれぞれのライセンスに基づいて使用しています。Android版に含まれる主な実行時依存ライブラリは、**本アプリのOSSライセンス** に記載されています。

本アプリの名称、アイコン、画面、説明、リポジトリ情報、開発者名、出典表記その他の表示を利用して改変版または派生版を配布する場合、国土交通省、または第三者の公式版、承認版、後援版であると誤認させないようにしてください。改変版を配布する場合は、Apache License, Version 2.0 の条件、適用法令、配布先ストアの規約、および第三者の権利を遵守してください。

ソースコードリポジトリにIssue、Pull Request、パッチ、翻訳、ドキュメント、アイデアその他の投稿を行う場合、その投稿は、投稿時に明示された別段の条件がない限り、Apache License, Version 2.0 に基づき本アプリへ取り込まれる可能性があります。

## 6. プライバシーと端末内データ

本アプリにおけるデータの取扱いは、別途定めるプライバシーポリシーに従います。

- プライバシーポリシー: **本アプリのプライバシーポリシー**を参照

本アプリは氏名やメールアドレス等の登録を求めず、端末の位置情報 API、端末識別子、広告 SDK、利用状況分析 SDK または独自のクラッシュ報告 SDK を使用しません。取得したダム諸量データ、ユーザー設定およびデバッグログを自動的にアップロードすることもありません。

ダム諸量データの出所は国土交通省の水文水質データベースです。リアルタイムデータと過去データの既定のネットワーク取得先は、本プロジェクト開発者が運営するキャッシュサーバ(https://sudmonitor.kusugami-lab.net)です。キャッシュサーバはリアルタイム、日次過去データおよび検索用月次データを配信します。設定で各取得先を国土交通省の配信元サーバ(https://www1.river.go.jp)からの直接取得へ変更でき、過去データ検索では必要に応じて同サーバへ直接アクセスします。

キャッシュサーバへの通信では、Cloudflare が送信元 IP アドレス、要求先、要求日時、User-Agent 等の通信情報をサービス提供、安全性確保、不正利用防止および障害調査のために処理します。詳細と保持期間はプライバシーポリシーを参照してください。

本アプリは、国土交通省から取得したダム諸量データ(リアルタイムデータ・過去データ)、ユーザー設定、デバッグログ等をアプリの保存領域に保存します。ユーザー設定は Android のバックアップ、端末移行により端末外へ移動する場合があります。その他の保存データは、本アプリの共有機能、画面キャプチャ、手動コピー、外部アプリへの共有等、利用者の操作または OS 機能により端末外へ移動する場合があります。ユーザー設定以外のデータやデバッグログは Android のバックアップ・端末移行対象外です。

保存済みの過去データ検索結果とデバッグログは、アプリ内の各削除機能で削除できます。自動取得される日次過去データはこれらの削除機能の対象外であり、完全に削除するには Android のアプリ情報画面から本アプリのストレージを消去するか、本アプリをアンインストールしてください。これらの操作では、その他のアプリデータも全て削除されます。ただし、OS のバックアップ、端末移行後のコピー、利用者が共有またはコピーしたファイル、外部アプリに渡したデータについては、本アプリから削除できない場合があります。

## 7. 権限、通知、バックグラウンド更新

本アプリは、ダム諸量データ取得、ネットワーク状態確認、通知表示、端末起動後の自動更新再開のため、Androidの権限を使用します。各権限の目的はプライバシーポリシーに記載します。

通知、自動更新、端末起動後の更新、ウィジェット更新は、Androidの仕様、端末メーカーの省電力機能、ネットワーク状態、ユーザー設定、通知権限、バックグラウンド制限等の影響を受けます。本アプリは、通知または自動更新が常に予定時刻どおりに実行されることを保証しません。

デバッグログは自動的に記録され、端末内に保存されます。エクスポートまたは共有は、利用者の操作により実行されます。利用者は、共有先、保存先、ファイル内容、第三者への提供の要否を自己の責任で確認してください。

## 8. 外部サービスとリンク

本アプリは、国土交通省関連ページ、地図アプリ、ブラウザ、OSの共有機能、Codeberg、GitHub等の外部サービスまたは外部アプリへのリンク、起動、共有を行う場合があります。

外部サービスまたは外部アプリの利用には、各提供者の規約、プライバシーポリシー、利用条件が適用されます。当方は、外部サービスまたは外部アプリの内容、可用性、正確性、安全性、変更、停止、利用結果について責任を負いません。

## 9. 利用者の責任と禁止事項

利用者は、本アプリを適用法令、本規約、配布元の規約、Androidおよび配布プラットフォームの規約、第三者の権利、ならびに社会通念に従って利用するものとします。

利用者は、次の行為を行ってはなりません。

- 法令、公序良俗、第三者の権利、本規約、配布元の規約に違反する行為
- 本アプリ、国土交通省の公開サーバ、キャッシュサーバ、Codeberg、GitHub、外部サービス、第三者の端末またはネットワークに過度の負荷、障害、不正アクセス、妨害を与える行為
- 本アプリまたは改変版を、国土交通省、またはその他第三者の公式版、承認版、後援版であると誤認させる行為
- マルウェア、不正コード、フィッシング、詐欺、なりすまし、虚偽表示、誤認表示、利用者を欺く目的で本アプリまたは改変版を利用または配布する行為
- 本アプリの表示データを、公式情報であるかのように提示し、第三者の重要判断を誤らせる行為
- その他、当方が本アプリの通常の利用範囲を逸脱すると合理的に判断する行為

## 10. 変更、中断、終了

当方は、必要に応じて、本アプリの機能、仕様、表示、対応OS、対応端末、配布方法、配布元、リポジトリ、ドキュメント、プライバシーポリシー、本規約を変更することがあります。

当方は、国土交通省の公開データ仕様の変更、外部サービスの停止、本アプリを配布する Codeberg/GitHub 等のプラットフォームの規約・ポリシー変更、F-Droid 等のポリシー変更、セキュリティ上の理由、保守、法令上の要請、その他やむを得ない事情により、本アプリの配布、更新、提供、サポートを中断または終了することがあります。

本規約を変更した場合、変更後の規約は、リポジトリ、アプリ内表示、配布ページ、または当方が適切と判断する方法で公開した時点から効力を生じます。変更後に本アプリを利用した場合、利用者は変更後の規約に同意したものとみなされます。

## 11. 非保証

本アプリは、現状有姿かつ提供可能な範囲で無償提供されます。当方は、本アプリについて、正確性、完全性、最新性、有用性、可用性、継続性、安全性、エラーがないこと、特定目的への適合性、第三者権利の非侵害、端末またはOSとの互換性、データの保存または復元、通知または自動更新の確実な実行を保証しません。

本アプリの利用、利用不能、データの表示、欠測、誤表示、更新遅延、通知遅延、端末内データの消失、外部サービスの利用、実行ファイルの取得またはインストール、改変版または第三者配布物の利用により生じた損害について、利用者は自己の責任で対応するものとします。

## 12. 責任の制限

当方は、法令上許される最大限の範囲で、本アプリに関連して利用者または第三者に生じた損害について責任を負いません。

法令上、当方が責任を免れない場合であっても、当方の故意または重過失による場合を除き、当方の責任は、現実に発生した通常かつ直接の損害に限られます。当方は、特別損害、間接損害、付随的損害、結果損害、逸失利益、データ消失、事業中断、端末故障、第三者からの請求、公式情報の確認を怠ったことによる損害について責任を負いません。

## 13. 未成年者の利用

未成年者が本アプリを利用する場合は、親権者その他の法定代理人の同意を得たうえで利用してください。未成年者が本アプリを利用した場合、法定代理人の同意を得たものとみなされます。

## 14. 準拠法と管轄

本規約の成立、効力、解釈、履行、および本アプリに関連する紛争には、日本法を準拠法とします。

本規約または本アプリに関連して当方と利用者との間で紛争が生じた場合、高松地方裁判所を第一審の専属的合意管轄裁判所とします。

## 15. お問い合わせ

本規約、本アプリ、プライバシーポリシー、ライセンスに関するお問い合わせは、原則としてソースコードリポジトリ(Codeberg)のIssueにて行ってください。

- Codeberg: https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android

当方は、すべてのお問い合わせへの個別回答、調査、修正、更新、サポート提供を保証しません。

    """.trimIndent()

}
