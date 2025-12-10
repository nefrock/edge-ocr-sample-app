# EdgeOCR SDK チュートリアル

EdgeOCR SDK（以下 SDK）の使い方をサンプルコードを交えて説明します。
サンプルコードは、app ディレクトリに含まれています。

チュートリアルでは Android Studio を用いての開発を想定していますが、gradle ベースのプロジェクトを扱える他の環境（VS Code など）でも使用可能です。

まず環境の構築方法・デバイスのアクティベーション方法を説明し、そのあとに簡単な例から順を追って説明します。

SDKのjavadocは[こちら](https://nefrock.github.io/edge-ocr-sample-app/sdk_javadoc/)です。

## 環境構築

本レポジトリーのサンプルアプリをビルドするには、SDK とモデルファイルをダウンロードする必要があります。
なお、SDK のバージョンと本サンプルアプリが想定する SDK のバージョンが一致している必要があります。
本レポジトリーの git tag と SDK のバージョンは一致していますので、git tag を確認してください。

SDK の zip を本レポジトリーのルートディレクトリーに配置し、解凍してください。`edge_ocr_sdk_maven` ディレクトリーが作成されます。
`settings.gradle` で以下のように参照されますので、もしパスが変わっている場合は変更してください。
```gradle
dependencyResolutionManagement {
    repositories {
        // ...
        maven {
            url "$rootDir/edge_ocr_sdk_maven"
        }
    }
}
```
本サンプルアプリが想定する SDK のバージョンは、`app/build.gradle` で指定されています。
```gradle
dependencies {
    // ...
    implementation "com.nefrock.edgeocr:edgeocr:1.0.0"
}
```
ダウンロードした SDK のバージョンと一致しない場合は、ビルドエラーが発生します。

モデルファイルのZIPを `app/src/main/assets` ディレクトリーに配置し、解凍してください。
モデルバージョンを含む名前のディレクトリーが作成されます。
展開ソフトによって、ZIPの内容が直接 `assets` に展開される場合もありますので、その場合はフォルダー
を新たに作成してください。
作成されたフォルダー名を `model` にリネームするか、 `MainActivity.java` の `"models"` の部分を
解凍したフォルダー名に変更してください。
```java
api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
```


ここまで完了したら Android Studio で example ディレクトリを開いて、ビルドをしてみてください。

## 目次

- [アクティベーション](./docs/activation.md)
- [最もシンプルな例](./docs/simple-text.md)
- [OCR結果を画面に表示する](./docs/boxesoverlay.md)
- [範囲を指定してOCR](./docs/crop.md)
- [テキスト範囲の検出時にフィルタを行う](./docs/detectionfilter.md)
- [textの複数回読み取りで精度を上げる](./docs/ntimes-scan.md)
- [バーコードスキャン](./docs/barcode.md)
- [Bitmap からの OCR](./docs/bitmap.md)
