# Privacy Policy

**Last updated: September 5, 2026**

> This English version is a translation of the Japanese original `PRIVACY-ja.md`. The Japanese original is the authoritative text. If there is any discrepancy or conflict between this English translation and the Japanese original, the Japanese original prevails.

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
