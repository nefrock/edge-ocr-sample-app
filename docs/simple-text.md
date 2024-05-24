## 最もシンプルな例

`最もシンプルな例` は、カメラを起動して画面に映った文字をログに出力するサンプルです。

ログはスマホの画面には表示されず、Android Studio での Logcat などに表示されます。

こちらの例はメイン画面の一番上の `SimpleTextScanner` ボタンを押すと起動される画面で、
`app/src/main/java/com/nefrock/edgeocr_example/simple_text/SimpleTextScannerActivity.java`に定義されています。

### カメラセットアップ
カメラの実装にはアンドロイドの [CameraX](https://developer.android.com/training/camerax?hl=ja) を使用しています。

端末のカメラ機能を利用するため `AndroidManifest.xml` に次の 3 行を追加する必要があります。

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
```

次に、カメラから送られてくる各フレームごとの画像に対して OCR する箇所について説明いたします。
この処理は同クラスの `startCamera` メソッドに記述されています。

まず、[CameraX.ImageAnalysis](https://developer.android.com/training/camerax/analyze?hl=ja) オブジェクトを作成します。

```Java
// Set up image analysis using EdgeOCR
imageAnalysis = new ImageAnalysis.Builder()
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build();
```

YUV_420_888 形式で画像を取得するようにしている点と、`STRATEGY_KEEP_ONLY_LATEST` を使って[ノンブロッキング](https://developer.android.com/reference/androidx/camera/core/ImageAnalysis?hl=ja#STRATEGY_KEEP_ONLY_LATEST)で解析を行っている点に注意してください。

次に ImageAnalysis に OCR の処理を記述します。

```Java
imageAnalysis.setAnalyzer(analysisExecutor, image -> {
    if (!api.isReady()) {
        image.close();
        return;
    }
    try {
        ScanResult scanResult = api.scan(image);
        List<Text> detections = scanResult.getTextDetections();
        for(Text detection: detections) {
            String text = detection.getText();
            if(!text.isEmpty()) {
                Log.d("EDGE_OCR_FIRST_EXAMPLE", "detected: " + text);
            }
        }
    } catch (EdgeError e) {
        Log.e("EdgeOCRExample", "[startCamera] Failed to analyze image", e);
    } finally {
        image.close();
    }
});
```

`ImageAnalysis.setAnalyzer` の第二引数でAnalyzerを定義しており、Analyzerの `analyze` メソッドで処理を記述しています。
このサンプルでは簡単のためAnalyzerは無名クラスで定義しています。

`api.scan` メソッドの戻り値は `ScanResult` オブジェクトです。
このオブジェクトから `getDetections` メソッドで OCR 結果である `Detection` オブジェクトが取得できます。
スキャン範囲内の対象物トのすべてをスキャンするので、複数の `Detection` オブジェクトが返されます。
また、対象物は `useModel` で選択したモデルによってテキスト、バーコード、またはその両方です。
`getDetections` の返す `Detection` オブジェクトを `Text` または `Barcode` にキャストして、それぞれの情報を取得できます。
文字列、またはバーコードのみを取得したい場合は、`getTextDetections` （または `getBarcodeDetections`）を使用してください．

また、カメラの解像度とスキャン範囲は異なっています。
詳しくは[SDK が解析する画像の範囲について](boxesoverlay.md#sdk-が解析する画像の範囲について)で解説を行います。

こちらのサンプルでは、`Detection` オブジェクトの中からテキストが空でないものをログに出力しています。
