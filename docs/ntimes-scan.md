## textの複数回読み取りで精度を上げる

EdgeOCRではOCRの精度を上げるために、同じテキストを複数回読み込んだ結果を採用するような機能を提供しています。
`app/src/main/java/com/nefrock/edgeocr_example/ntimes_scan` に実装例がありますので、ご参考にしてください。
このサンプルでは、123-4567のような郵便番号を読み取り対象としています。

`app/src/main/java/com/nefrock/edgeocr_example/MainActivity.java` で読み取り回数の設定を行っています。
読み取り回数の設定は `ModelSettings#setNToConfirm` メソッドで行います。
読み取り回数を設定した `ModelSettings` オブジェクトを `EdgeVisionAPI` の `useModel` メソッドの引数として渡すことで、読み取り回数を設定したOCRを行うことができます。
5回同じ内容を読み取った場合にテキストを確定するように設定しています。
デフォルトのテキスト読み取り確定までの回数は3回です。
```Java
@ExperimentalCamera2Interop public class MainActivity extends AppCompatActivity {
    ...
    @Override
    public void onCreate(Bundle savedInstanceState) {
        ...
        findViewById(R.id.ntimes_scan_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), NtimesTextScanActivity.class);
            ModelSettings settings = new ModelSettings();
            settings.setNToConfirm(5);
            settings.setTextMapper(new PostCodeTextMapper());
            loadModelAndStartActivity(intent, settings);
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
    }
}
```

`app/src/main/java/com/nefrock/edgeocr_example/ntimes_scan/PostCodeRegexTextAnalyzer.java` において、読み取り結果のフィルタリングを行っています。
`Detection.getStatus` メソッドの返り値が、`ScanConfirmationStatus.Confirmed` かどうかで読み取り結果が確定しているかどうかを判定しています。
```Java
class PostCodeRegexTextAnalyzer implements ImageAnalysis.Analyzer {
    ...
    public PostCodeRegexTextAnalyzer(EdgeVisionAPI api) {
        ...
        this.regexPattern = Pattern.compile("(\\d{3})-(\\d{4})");
    }

    ...

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");

            ScanResult scanResult = api.scanTexts(image);
            List<Detection<Text>> filteredDetections = new ArrayList<>();
            List<Detection<Text>> notTargetDetections = new ArrayList<>();
            for (Detection<Text> detection : scanResult.getTextDetections()) {
                String text = detection.getScanObject().getText();
                Matcher matcher = regexPattern.matcher(text);
                if(matcher.find()) {
                    if (detection.getStatus() == ScanConfirmationStatus.Confirmed) {
                        filteredDetections.add(detection);
                    } else {
                        notTargetDetections.add(detection);
                    }
                }
            }
            callback.call(filteredDetections, notTargetDetections);
        } catch (EdgeError e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    ...

}
```


### TextMapper
複数回読み取りを行う間に、手ブレやカメラの移動などによって読み取り範囲が変化してしまうと、読み取り結果が異なってしまい、読み取り回数のカウントがリセットされてしまいます。
そこで、以前の結果と読み取り結果を比較する前に `TextMapper` を用いて読み取り結果を正規化することで、読み取り範囲の変化による影響を軽減することができます。
`TextMapper` クラスを継承し、`apply` メソッドを実装することで、TextMapperを作成します。
読み取り対象が郵便番号なので、英字や記号を数字に変換する処理を実装しています。
また、郵便番号のみを抽出するための正規表現も実装しています。
```Java
```Java
public class PostCodeTextMapper extends TextMapper {
    private final Pattern regexPattern;
    public PostCodeTextMapper() {
        //123-4567のような郵便番号をスキャンする
        regexPattern = Pattern.compile("^.*((\\d{3})-(\\d{4})).*$");
    }
    @Override
    public String apply(Text text) {
        String t = text.getText();
        t = t.replace("A", "4");
        t = t.replace("B", "8");
        t = t.replace("b", "6");
        t = t.replace("C", "0");
        t = t.replace("D", "0");
        t = t.replace("G", "6");
        t = t.replace("g", "9");
        t = t.replace("I", "1");
        t = t.replace("i", "1");
        t = t.replace("l", "1");
        t = t.replace("O", "0");
        t = t.replace("o", "0");
        t = t.replace("Q", "0");
        t = t.replace("q", "9");
        t = t.replace("S", "5");
        t = t.replace("s", "5");
        t = t.replace("U", "0");
        t = t.replace("Z", "2");
        t = t.replace("z", "2");
        t = t.replace("/", "1");

        Matcher m = regexPattern.matcher(t);
        if (m.find()) {
            t = m.group(1);
        }

        return t;
    }
}
```

作成した `TextMapper` を `ModelSettings` クラスの `setTextMapper` メソッドを用いて設定します。
`app/src/main/java/com/nefrock/edgeocr_example/MainActivity.java`で実装しています。

```Java
@ExperimentalCamera2Interop public class MainActivity extends AppCompatActivity {
    ...
    @Override
    public void onCreate(Bundle savedInstanceState) {
        ...
        findViewById(R.id.ntimes_scan_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), NtimesTextScanActivity.class);
            ModelSettings settings = new ModelSettings();
            settings.setNToConfirm(5);
            settings.setTextMapper(new PostCodeTextMapper());
            loadModelAndStartActivity(intent, settings);
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
    }
}
```
