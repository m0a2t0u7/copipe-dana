# AI向けガイド（ラテコピ）

タップでコピーするAndroid定型文アプリ。Kotlin + Jetpack Compose。

## 主要ファイル
- `app/src/main/java/com/shinyalatte/copipecho/Store.kt` データ（フォルダ・定型文・設定）とJSON保存
- `app/src/main/java/com/shinyalatte/copipecho/MainActivity.kt` 画面すべて（一覧、並べ替えモード、ダイアログ、設定）
- `app/src/main/java/com/shinyalatte/copipecho/OpenTileService.kt` クイック設定タイル

## 配布
- main にpushすれば GitHub Actions がAPKをビルドし、Releases の latest と Google Drive「自作アプリAPK/copipe-dana」に自動配布する
- 完了報告に入れるリンク
  - 最新版: https://github.com/m0a2t0u7/copipe-dana/releases/latest/download/copipe-dana.apk
  - ビルド状況: https://github.com/m0a2t0u7/copipe-dana/actions

## 消してはいけないもの
- `app/copipecho.jks` と `app/build.gradle.kts` の signingConfigs（消すと上書きインストールできなくなる）
- 保存データ形式（`Store.toJson` / `fromJson`）は後方互換を保つ
