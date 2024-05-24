## テキスト範囲の検出時にフィルタを行う

EdgeOCRでは、テキスト範囲の検出 -> テキスト認識の流れでOCRを行っています。
テキスト範囲の検出時に、範囲の位置やサイズの情報を用いてフィルタを行うことができます。

`app/src/main/java/com/nefrock/edgeocr_example/detection_filter`では、読み取り範囲の中心に最も近いテキストのみを読み取るフィルタを実装しています。

### 実装
```Java
public class GetCenterDetectionFilter extends DetectionFilter {
    @Override
    public List<Detection> filter(List<Detection> list) {
        Detection mostLikelyBox = null;
        double minDistance = Double.MAX_VALUE;
        for (Detection detection: list) {
            RectF box = detection.getBoundingBox();
            double distance = Math.pow(box.centerX()-0.5, 2) + Math.pow(box.centerY()-0.5, 2);
            if (distance < minDistance) {
                minDistance = distance;
                mostLikelyBox = detection;
            }
        }
        if ( mostLikelyBox != null) {
            return Collections.singletonList(mostLikelyBox);
        } else {
            return Collections.emptyList();
        }
    }
}
```

`DetectionFilter` クラスを継承し、`filter`メソッドをオーバーライドすることでDetectionFilterを作成します。
画像内に対して検出されたテキストの範囲のリストが引数として渡されるので、このリストを加工して返すことでフィルタを実現します。
テキストの範囲を `RectF` として取得しフィルタの実装を行います。
文字情報の読み取りはまだ行われていないので、`Text` または `Barcode` にキャストすることはできません。
このサンプルでは、画像の中心に最も近いテキストのみを返すフィルタを作成しています。


作成したフィルタをEdgeOCRに設定するには、`ModelSettings` クラスの `setDetectionFilter` メソッドを用います。
フィルタをセットした `ModelSettings` オブジェクトを `EdgeVisionAPI` の `useModel` メソッドの引数として渡すことで、フィルタを適用したOCRを行うことができます。
`app/src/main/java/com/nefrock/edgeocr_example/MainActivity.java` でフィルタのセットを行っています。

```Java
...

public void onCreate(Bundle savedInstanceState) {

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

private void loadModelAndStartActivity(Intent intent, ModelSettings modelSettings) {

    ...

    api.useModel(model, modelSettings, modelInformation -> {
        intent.putExtra("model_aspect_ratio", modelInformation.getAspectRatio());
        startActivity(intent);
    }, edgeError -> Toast.makeText(getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG)
        .show());

    ...
}
```
