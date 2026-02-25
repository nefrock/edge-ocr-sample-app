### モデル選択とロード
モデルのアスペクト比を用いて、OCR結果の描画範囲を決定するため、OCR画面に遷移する前にモデルのロードを行うことを推奨します。
本サンプルアプリではMainActivity.javaで、各サンプルActivityへの遷移前にモデルのロードを行っています。


```Java
...

@Override
public void onCreate(Bundle savedInstanceState) {
    ...

    findViewById(R.id.simple_ocr_button).setOnClickListener(view -> {
        Intent intent = new Intent(getApplication(), SimpleTextScannerActivity.class);
        loadModelAndStartActivity(intent);
    });

    ...

    findViewById(R.id.detection_filter_button).setOnClickListener(view -> {
        Intent intent = new Intent(getApplication(), DetectionFilterScannerActivity.class);
        ModelSettings modelSettings = new ModelSettings();
        modelSettings.setDetectionFilter(new GetCenterDetectionFilter());
        loadModelAndStartActivity(intent, modelSettings);
    });

    ...
}

...

private void loadModelAndStartActivity(Intent intent) {
        loadModelAndStartActivity(intent, new ModelSettings());
    }

    private void loadModelAndStartActivity(Intent intent, ModelSettings modelSettings) {
        runOnUiThread(()->findViewById(R.id.progressLayout).setVisibility(android.view.View.VISIBLE));
        EdgeVisionAPI api;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
        } catch (EdgeError e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Model model = null;

        for (Model candidate : api.availableModels()) {
            if (candidate.getUID().equals("model-large")) {
                model = candidate;
                break;
            }
        }
        if (model == null) {
            Toast.makeText(getApplicationContext(), "モデルが見つかりません", Toast.LENGTH_LONG).show();
            return;
        }
        api.useModel(model, modelSettings, modelInformation -> {
            intent.putExtra("model_aspect_ratio", modelInformation.getAspectRatio());
            startActivity(intent);
        }, edgeError -> Toast.makeText(getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG)
            .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.progressLayout).setVisibility(android.view.View.GONE);
    }
```

`loadModelAndStartActivity`では、`api.useModel`でモデルをロードし、ロードが完了したら各サンプルActivityへ遷移しています。
遷移前にOCRの結果表示に用いるモデルのアスペクト比をintentにセットしています。

`api.useModel`の第1引数には、`Model`を指定します。
使用可能なモデルはモデルフォルダーにある`models.json`をご参考にしてください。
指定できるモデルは使用しているEdgeOCRエンジンのバージョンによって異なります。
一般的なモデルのモデル名に `model-d<width>x<height>` のように、モデルの解像度が含まれています。
モデルの解像度が高いほど、精度が向上しますが、処理速度が低下します。
ユースケースやデバイスのスペックに合わせてどのモデルを使うかを選択していただけます。
また、カスタマイズしたモデルを利用する場合もこちらで指定を行います。
バーコードモデルとしては、 `edgeocr_barcode_default` を指定してください．
実験的な高度なバーコード認識を行う場合は、 `edgeocr_barcode_advanced` を指定してください。

また`api.useModel`の第2引数には、`ModelSettings`を指定することができます。
`ModelSettings`では、モデルパラメータの設定や、検出結果のフィルタ設定、`TextMapper`の設定を行うことができます。

### GPU を使用するモデルのロードにかかる時間について

GPU を使用するモデルは、初回のロード時のみロード時間が数秒かかります。
この時間はローエンドのデバイスほど時間がかかる傾向にあります。
ただし 2 回目以降のロードは高速に処理され、アプリを削除しない限りはロードに数秒かかることはありません。
