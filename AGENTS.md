# Repository Guidelines

## プロジェクト構成 / モジュール
- ルート: Gradle ラッパー・設定（`build.gradle`、`settings.gradle`）。
- アプリ本体: `app/`（Android Application モジュール、Java 17）。
- ソース/リソース: `app/src/main/java`、`app/src/main/res`、`app/src/main/AndroidManifest.xml`。
- アセット: `app/src/main/assets/`（例: `models/` 配下にモデルを配置）。
- SDK 参照: ルート直下の `edge_ocr_sdk_maven/` をローカル Maven として使用（`.gitignore` 済み）。
- ドキュメント: `docs/`（機能別チュートリアル、Javadoc）。
 - 概要: 各 Activity が `EdgeVisionAPI` でモデルをロードし、CameraX でプレビュー解析。

## ビルド・実行・開発
- `./gradlew assembleDebug` デバッグ APK をビルド。
- `./gradlew installDebug` 接続端末/エミュレータへインストール。
- `./gradlew clean` クリーン。 `./gradlew lint` Lint 実行。
- 実行例: `adb shell am start -n com.nefrock.edgeocr_example/.MainActivity`。
- 前提: JDK 17、Android Gradle Plugin 8.12、`compileSdk=36`/`minSdk=26`。`edge_ocr_sdk_maven` と `assets/models` を事前配置。

## コーディング規約 / 命名
- 言語: Java（Android）。インデント 4 スペース、公式フォーマッタ準拠。
- クラス: `UpperCamelCase`（例: `MainActivity`）。メソッド/フィールド: `lowerCamelCase`。
- Activity は `*Activity`、アナライザは `*Analyzer` とし、パッケージで機能分割（例: `barcode/`, `crop/`）。
- リソース: レイアウト `activity_*.xml`、ID/名前は `snake_case`。文言は `res/values/strings.xml` 管理。

## テスト方針
- 現状テスト未整備。追加する場合:
  - 単体: `app/src/test/java/...`（JUnit4 などを依存に追加）→ `./gradlew test`。
  - 計装: `app/src/androidTest/java/...`（Espresso 等）→ `./gradlew connectedAndroidTest`。
- カバレッジ目安: 重要ロジック（アナライザ/マッパー）を優先し 60% 以上を推奨。

## コミット / PR
- コミットは短い日本語の要約が基本（履歴例: 「EdgeOCR SDK 3.7.0 に対応したサンプルアプリ」）。必要に応じて `feat:`/`fix:`/`chore:` を先頭に付与可。
- PR には目的・変更点・確認手順・スクリーンショット（UI 変更時）・関連 Issue を記載。`docs/` 更新があれば併記。
- ブランチ名: `feature/<topic>` / `fix/<topic>` / `chore/<topic>`。

## セキュリティ / 設定メモ
- ライセンスキーはコミット禁止。`MainActivity` の `NefrockLicenseAPI.withLicenseKey(...)` は有効キーを設定するか、検証時は該当ブロックを一時コメントアウト。
- 大容量モデルは Git に含めない（`assets/models` は各自配置）。`settings.gradle` のローカル Maven パスが合うか確認。
