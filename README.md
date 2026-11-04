[日本語版はこちら](README-ja.md)

# Sameura Dam Monitor

> [!CAUTION]
> **Important Notice: Temporary Workaround for Sameura Dam Data and Remaining Limitations (Updated November 5, 2026)**
>
> Following the data retrieval disruption caused by changes to MLIT's Water Information System website on November 4, 2026, we have implemented a temporary workaround on this project's cache server (`sudmonitor.kusugami-lab.net`) to retrieve real-time and historical data for Sameura Dam.
>
> **Real-time and historical data for Sameura Dam are available through the cache server.** We have confirmed that regular real-time updates are working and that the daily historical data updated successfully after midnight on November 5 (Japan Standard Time). Please use the cache server as the data source in the app settings.
>
> **Direct retrieval from MLIT's Water Information System within the app continues to fail for both real-time and historical data.** Historical searches may also fail when they require direct access to MLIT, such as when the requested period is not available on the cache server.
>
> This temporary workaround applies to Sameura Dam. Support for other dams and direct retrieval from MLIT has not yet been restored. Please also check the date and time of the displayed data.
>
> If data cannot be retrieved in the app, please search for an observation station on the [official MLIT Water Information System website](https://www1.river.go.jp/). Viewing the official website in a browser is separate from direct data retrieval within the app.
>
> There is currently no estimated restoration date for the remaining functionality. Progress will be posted in this README.
>
> We apologize for the inconvenience.

**Sameura Dam Monitor** is an Android app that retrieves dam data published through the Water Information System of the Ministry of Land, Infrastructure, Transport and Tourism of Japan and displays the water storage rate and related data for 125 dams across Japan, including Sameura Dam. The Android app is available through F-Droid and as signed APKs through GitHub Releases. Google Play distribution is currently being prepared for open testing.

This app is optimized for udon noodle lovers in Kagawa Prefecture who worry about Sameura Dam's water storage rate.

**This app is not an official app provided or approved by MLIT or any other government agency or third party.**

## Features

- **Real-time data display** — Fetch and save real-time data; display water storage rate, storage volume, inflow, outflow, and catchment average rainfall
- **Storage rate message display** — Display status and message based on the water storage rate, with customization support
- **Historical data display** — Retrieve and save past dam data for any date range; offline search support (Sameura Dam only)
- **Interactive graphs** — Graph display of real-time and historical data (rainfall/storage rate; storage volume/inflow/outflow)
- **Home screen widget** — Check the real-time storage rate and message at a glance
- **Auto-update & notifications** — Background real-time data fetching and water storage rate change notifications
- **Multi-language support** — Japanese and English

## How to Obtain

Install the app from [F-Droid](https://f-droid.org/packages/net.tecogonaz.tcsameuradammonitor/), or download an APK from GitHub Releases:

1. Download the latest signed APK from [GitHub Releases](https://github.com/tecogonaz0439/TCSameuraDamMonitor-android/releases).
2. Open the downloaded APK file to install it.
   - If a security warning ("Install unknown apps") appears during installation, allow installation from the settings on your device.

## Requirements

- Android 14 (API 34) or later

## Data Source

The data originates from MLIT's Water Information System. By default, the app retrieves both real-time data and historical data (daily and monthly data used for searches) through the cache server operated by this project. You can select direct retrieval from MLIT in the app settings, and historical searches may access MLIT directly when necessary.

Due to changes to the Water Information System website, direct retrieval from MLIT within the app is currently unavailable for both real-time and historical data. A temporary workaround enables retrieval of Sameura Dam data through the cache server. Historical searches may still fail when they require direct access to MLIT, such as when the requested period is not available on the cache server. Please see the important notice at the top of this README for details.

- `https://sudmonitor.kusugami-lab.net`
- `https://www1.river.go.jp`

## Repository

- **GitHub**: [tecogonaz0439/TCSameuraDamMonitor-android](https://github.com/tecogonaz0439/TCSameuraDamMonitor-android)

Please report issues and submit pull requests in this repository.

## Links

- Terms of Use: [English](docs/TERMS.md) / [Japanese](docs/TERMS-ja.md)
- Privacy Policy: [English](docs/PRIVACY.md) / [Japanese](docs/PRIVACY-ja.md)
- OSS License: [OSS-LICENSE.md](docs/OSS-LICENSE.md)
- Security Policy: [SECURITY.md](SECURITY.md)

## License

The source code is licensed under the [Apache License, Version 2.0](LICENSE).

The F-Droid link icon is an adaptation of the F-Droid logo, licensed under CC-BY-SA-3.0. See [artwork attribution](artwork/fdroid/README.md).
