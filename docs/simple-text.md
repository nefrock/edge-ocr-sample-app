## 最もシンプルな例

`最もシンプルな例` は、カメラを起動して画面に映った文字をログに出力するサンプルです。

ログはスマホの画面には表示されず、Android Studio での Logcat などに表示されます。

こちらの例はメイン画面の一番上の `SimpleTextScanner` ボタンを押すと起動される画面で、
`app/src/main/java/com/nefrock/edgeocr_example/simple_text/SimpleTextScannerActivity.java`に定義されています。

### モデル選択とロード

このクラスでは`OnCreate`でアクティビティが作成された時に API を初期化し、使用するモデルを選択しています。

```Java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_simple_text_scanner);
    // Initialize EdgeOCR
    Model model = null;
    try {
        api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
        for (Model candidate : api.availableModels()) {
            if (candidate.getUID().equals("model-large")) {
                model = candidate;
                break;
            }
        }
    } catch (Exception e) {
        Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
        return;
    }

    if (model == null || api == null) {
        Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR");
        return;
    }

    api.useModel(model, (ModelInformation modelInformation) -> {
        //モデルがロードされた時の処理を定義
    }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
    if (cameraPermissionGranted()) {
        startCamera();
    } else {
        ActivityCompat.requestPermissions(
                this, new String[]{"android.permission.CAMERA"}, 10);
    }
}
```

まずはじめに api を `assets/models` ディレクトリの内容で初期化し、その後 `api.useModel` で api に `model-large` を使うように指定しています。

SDK のデフォルトでは `model-small`、 `model-large` が指定できます。
`model-large` は`model-small`に比べて高精度のモデルで、OCRに時間がかかります。
ユースケースやデバイスのスペックに合わせてどのモデルを使うかを選択していただけます。
また、カスタマイズしたモデルを利用する場合もこちらで指定を行います。

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

YUV_420_888 形式で画像を取得するようにしている点と、`STRATEGY_KEEP_ONLY_LATEST` を使って[ノンブロッキング](https://developer.android.com/reference/androidx/camera/core/ImageAnalysis?hl=ja#STRATEGY_KEEP_ONLY_LATEST)で解析を行って行っている点に注意してください。

次に ImageAnalysis に OCR の処理を記述します。

```Java
imageAnalysis.setAnalyzer(analysisExecutor, image -> {
    if (!api.isReady()) {
        image.close();
        return;
    }
    try {
        ScanResult scanResult = api.scanTexts(image);
        List<Detection<Text>> detections = scanResult.getTextDetections();
        for(Detection<Text> detection: detections) {
            String text = detection.getScanObject().getText();
            if(!text.isEmpty()) {
                Log.d("EDGE_OCR_FIRST_EXAMPLE", "detected: " + detection.getScanObject().getText());
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

`api.scanTexts` メソッドの戻り値は `ScanResult` オブジェクトです。
このオブジェクトから `getDetections` メソッドで OCR 結果である `Detection` オブジェクトが取得できます。
スキャン範囲内のテキストのすべてをスキャンするので、複数の `Detection` オブジェクトが返されます。

また、カメラの解像度とスキャン範囲は異なっています。
詳しくは[SDK が解析する画像の範囲について](boxesoverlay.md#sdk-が解析する画像の範囲について)で解説を行います。

`Detection` オブジェクトでは読み取り対象ごとに対応する `ScanObject` オブジェクトが返されます。
`Text` の `ScanObject` から、読み取り結果のテキストを取得できます。
こちらのサンプルでは、`Detection` オブジェクトの中からテキストが空でないものをログに出力しています。

### GPU を使用するモデルのロードにかかる時間について

GPU を使用するモデルは、初回のロード時のみロード時間が数秒かかります。
この時間はローエンドのデバイスほど時間がかかる傾向にあります。
ただし 2 回目以降のロードは高速に処理され、アプリを削除しない限りはロードに数秒かかることはありません。
