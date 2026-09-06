# Terms of Use

**Last updated: September 5, 2026**

> This English version is a translation of the Japanese original `TERMS-ja.md`. The Japanese original is the authoritative text. If there is any discrepancy or conflict between this English translation and the Japanese original, the Japanese original prevails.

## 1. Scope

These Terms of Use ("Terms") set out the conditions for using **Sameura Dam Monitor** (Japanese name: 早明浦ダム 貯水率モニタ, the "App").

By installing, launching, using, or obtaining the App from an official distribution source, you are deemed to have agreed to these Terms. If you do not agree to these Terms, do not use the App.

These Terms define the conditions for using the App. They do not restrict the rights to use, reproduce, modify, distribute, or otherwise exercise rights in the source code, object code, derivative works, or redistributions granted under the Apache License, Version 2.0. Those materials are governed by [LICENSE](../LICENSE) and [OSS-LICENSE.md](OSS-LICENSE.md).

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

The App uses multiple open source libraries under their respective licenses. Major runtime dependencies included in the Android version are described in `OSS-LICENSE.md`.

If you distribute a modified or derivative version using the App's name, icon, screens, description, repository information, developer name, attribution notices, or similar materials, do not mislead users into believing that it is an official version, approved version, or sponsored version of MLIT or any other third party. If you distribute a modified version, comply with the Apache License, Version 2.0, applicable laws, the terms of the distribution store, and third-party rights.

If you submit Issues, Pull Requests, patches, translations, documentation, ideas, or other contributions to the source code repository, those contributions may be incorporated into the App under the Apache License, Version 2.0, unless expressly stated otherwise at the time of submission.

## 6. Privacy and On-Device Data

The handling of data in the App is governed by the separate Privacy Policy.

- Privacy Policy: see [PRIVACY.md](PRIVACY.md)

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
