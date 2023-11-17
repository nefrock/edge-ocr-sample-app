## テキスト範囲の検出時にフィルタを行う

EdgeOCRでは、テキスト範囲の検出 -> テキスト認識の流れでOCRを行っています。  
テキスト範囲の検出時に、範囲の位置やサイズの情報を用いてフィルタを行うことができます。

`app/src/main/java/com/nefrock/edgeocr_example/detection_filter`では、読み取り範囲の中心に最も近いテキストのみを読み取るフィルタを実装しています。

### 実装
```Java
public class DetectionFilterScannerActivity extends AppCompatActivity {

...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ...

        api.useModel(model, (ModelInformation modelInformation) -> {

            ... 

            api.setDetectionFilter(new GetCenterDetectionFilter());

            ...

        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
        ...
    }

    ...

    static class GetCenterDetectionFilter extends DetectionFilter {
        @Override
        public List<Detection<? extends ScanObject>> filter(List<Detection<? extends ScanObject>> list) {
            Detection<? extends ScanObject> mostLikelyBox = null;
            double minDistance = Double.MAX_VALUE;
            for (Detection<? extends ScanObject> detection: list) {
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

...

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        ...

        api.clearDetectionFilter();
    }

...

}

```

`DetectionFilter` クラスを継承し、`filter`メソッドをオーバーライドすることでDetectionFilterを作成します。  
画像内に対して検出されたテキストの範囲のリストが引数として渡されるので、このリストを加工して返すことでフィルタを実現します。  
テキストの範囲を `RectF` として取得しフィルタの実装を行います。  
文字情報の読み取りはまだ行われていないので、`ScanObject` の情報は利用できない点にご注意ください。  
このサンプルでは、画像の中心に最も近いテキストのみを返すフィルタを作成しています。  

作成したフィルタをEdgeOCRに設定するには、`EdgeOCR#setDetectionFilter` メソッドを使用します。  
apiは内部で状態を保持しているため、スキャンを終了する際は `EdgeOCR#clearDetectionFilter` メソッドを使用してフィルタをクリアしてください。