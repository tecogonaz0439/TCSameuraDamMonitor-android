[日本語版はこちら](README-ja.md)

# Sameura Dam Monitor

**Sameura Dam Monitor** is an Android app that retrieves dam data published through the Water Information System of the Ministry of Land, Infrastructure, Transport and Tourism of Japan and displays the water storage rate and related data for 125 dams across Japan, including Sameura Dam. The Android app is currently distributed directly as a built APK file through the Codeberg/GitHub Releases pages. Distribution through app stores such as F-Droid or Google Play is planned for a later date.

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

1. Download the latest signed APK attached to the repository's [Releases](https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android/releases) page.
2. Open the downloaded APK file to install it.
   - If a security warning ("Install unknown apps") appears during installation, allow installation from the settings on your device.

## Requirements

- Android 14 (API 34) or later

## Data Source

The data originates from MLIT's Water Information System. By default, the app retrieves both real-time data and historical data (daily and monthly data used for searches) through the cache server operated by this project. You can select direct retrieval from MLIT in the app settings, and historical searches may access MLIT directly when necessary.

- `https://sudmonitor.kusugami-lab.net`
- `https://www1.river.go.jp`

## Repositories

- **Codeberg** : [tecogonaz0439/TCSameuraDamMonitor-android](https://codeberg.org/tecogonaz0439/TCSameuraDamMonitor-android)
- **GitHub** : [tecogonaz0439/TCSameuraDamMonitor-android](https://github.com/tecogonaz0439/TCSameuraDamMonitor-android)

GitHub is a mirror of Codeberg and is **read-only**. Issues and pull requests are only accepted on Codeberg.

## Links

- Terms of Use: [English](docs/TERMS.md) / [Japanese](docs/TERMS-ja.md)
- Privacy Policy: [English](docs/PRIVACY.md) / [Japanese](docs/PRIVACY-ja.md)
- OSS License: [OSS-LICENSE.md](docs/OSS-LICENSE.md)
- Security Policy: [SECURITY.md](SECURITY.md)

## License

The source code is licensed under the [Apache License, Version 2.0](LICENSE).
