[English version](README.md)

# 早明浦ダム 貯水率モニタ

**早明浦ダム 貯水率モニタ**は、国土交通省の水文水質データベースからダム諸量データを取得し、早明浦ダムを含む国内 125 ダムの貯水率と関連データを表示する Android アプリです。F-Droid と GitHub Releases で配布しています。Google Play では公開テストへの移行を準備しています。

本アプリは、早明浦ダムの貯水率を心配するうどん県民（香川県民）向けに最適化されています。

**本アプリは、国土交通省を含む政府機関、またはその他の第三者が提供・承認する公式アプリではありません。**

## 機能

- **リアルタイムデータ表示** — リアルタイムデータを取得・保存、貯水率、貯水量、流入量、放流量、流域平均雨量を表示
- **貯水率メッセージ表示** — 貯水率に応じた状態とメッセージを表示、カスタマイズ対応
- **過去データ表示** — 任意期間の過去データを取得・保存、オフライン検索対応(早明浦ダムのみ)
- **インタラクティブグラフ** — リアルタイムデータ・過去データのグラフ表示(雨量/貯水率、貯水量/流入量/放流量)
- **ホーム画面ウィジェット** — リアルタイムの貯水率とメッセージを一目で確認
- **自動更新・通知** — バックグラウンドでのリアルタイムデータ取得と貯水率変化通知
- **多言語対応** — 日本語・英語

## 入手方法

[F-Droid](https://f-droid.org/ja/packages/net.tecogonaz.tcsameuradammonitor/) からインストールするか、GitHub Releases からAPKをダウンロードしてください。

1. [GitHub Releases](https://github.com/tecogonaz0439/TCSameuraDamMonitor-android/releases) から最新の署名済み APK をダウンロードします。
2. ダウンロードした APK ファイルを開いてインストールします。
   - インストール時にセキュリティ警告（「提供元不明のアプリ」）が表示される場合は、端末の設定からこのファイルのインストールを許可してください。

## 動作要件

- Android 14 (API 34) 以上

## データソース

データの出所は国土交通省の水文水質データベースです。アプリの既定では、リアルタイムデータと過去データ（日次・検索用月次）を本プロジェクトが運営するキャッシュサーバから取得します。設定で水文水質データベース（MLIT）からの直接取得へ変更でき、過去データ検索では必要に応じて MLIT へ直接アクセスします。

- `https://sudmonitor.kusugami-lab.net`
- `https://www1.river.go.jp`

## リポジトリ

- **GitHub**: [tecogonaz0439/TCSameuraDamMonitor-android](https://github.com/tecogonaz0439/TCSameuraDamMonitor-android)

不具合の報告や提案は、GitHub の Issue からお寄せください。Pull Request も同リポジトリで受け付けています。

## リンク

- 利用規約: [English](docs/TERMS.md) / [日本語](docs/TERMS-ja.md)
- プライバシーポリシー: [English](docs/PRIVACY.md) / [日本語](docs/PRIVACY-ja.md)
- OSSライセンス: [OSS-LICENSE.md](docs/OSS-LICENSE.md)
- セキュリティポリシー: [SECURITY.md](SECURITY.md)

## ライセンス

ソースコードは [Apache License, Version 2.0](LICENSE) の下で公開されています。

F-Droidリンクのアイコンは、CC BY-SA 3.0のF-Droidロゴを加工したものです。[素材の帰属と加工内容](artwork/fdroid/README.md)を参照してください。
