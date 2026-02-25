## バーコードスキャン

EdgeOCR ではバーコードのスキャンも可能です。

`app/src/main/java/com/nefrock/edgeocr_example/barcode` に実装例がありますので、ご参考にしてください。

`app/src/main/java/com/nefrock/edgeocr_example/MainActivity.java`でバーコードフォーマット毎の確定までの読み取り回数を設定しています。
サンプルでは QR コードのみ 5 回読み取った後、結果を確定するように設定しています。
それ以外のバーコードフォーマットは読み取り回数を指定していないため、デフォルトである 1 回の読み取り後に結果を確定します。
また `useModel` でバーコードを読み取るためのモデルを指定します。

```Java
public class MainActivity extends AppCompatActivity {
    // ...
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ...
        findViewById(R.id.barcode_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeScannerActivity.class);
            intent.putExtra("show_dialog", true);
            ModelSettings modelSettings = new ModelSettings();
            modelSettings.setBarcodeNToConfirm(Collections.singletonMap(BarcodeFormat.QRCode, 5));
            // バーコードの読み取りには edgeocr_barcode_default モデルを使用します
            loadModelAndStartActivity(intent, "edgeocr_barcode_default", modelSettings);
        });
        // ...
    }
    // ...
}
```

OCR と同時にバーコードをスキャンすることも可能です。
OCR に使用しているモデルの UID に `_with_barcode` を付与したモデルを使用することで、OCR とバーコードスキャンの両方を同時に実行できます。
`app/src/main/java/com/nefrock/edgeocr_example/MainActivity.java` の `camera_overlay_button` のクリックリスナーで OCR とバーコードスキャンを同時に実行する例を示します。
```Java
public class MainActivity extends AppCompatActivity {
    // ...
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        findViewById(R.id.camera_overlay_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), CameraOverlayTextScannerActivity.class);
            loadModelAndStartActivity(intent, "model-d320x320_with_barcode");
        });
        // ...
    }
    // ...
}
```

### バーコードスキャンの設定

`app/src/main/java/com/nefrock/edgeocr_example/barcode/BarcodeAnalyzer.java` においてバーコードスキャンを実行しています。

OCR の場合と同じように `scan` メソッドを使用してバーコードをスキャンします．
こちらのサンプルではすべてのバーコードのフォーマットを読み取るようにしていますが、`ScanOptions`の`setBarcodeFormats`を以って複数のフォーマットを指定することも可能です。

また、`app/src/main/java/com/nefrock/edgeocr_example/barcode/BarcodeScannerActivity.java`で設定した読み取り回数を超えた場合は、`detection.getStatus()`で`ScanConfirmationStatus.Confirmed`が返ります。
本サンプルでは読み取り回数を超えたバーコードのみを結果として表示しています。

`showDialog`でスキャン結果の表示後、`api.resetScanningState`を呼び出すことで、API のスキャン状況をリセットしています。これにより、バーコードの確定までの読み取り回数をリセットすることができます。

```Java
class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
    // ...

    public BarcodeAnalyser(EdgeVisionAPI api) {
        this.api = api;
        this.scanOption = new BarcodeScanOption(Collections.singletonList(BarcodeFormat.Any));
        this.isActive = true;
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            List<Barcode> targetDetections = new ArrayList<>();
            for (Barcode detection : scanResult.getBarcodeDetections()) {
                if (detection.getStatus() == ScanConfirmationStatus.Confirmed) {
                    targetDetections.add(detection);
                }
            }
            callback.call(targetDetections);
        } catch (EdgeError e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }
    // ...
    private void showDialog(List<Barcode> detections) {
        barcodeAnalyzer.stop();
        StringBuilder messageBuilder = new StringBuilder();
        for (Barcode detection : detections) {
            messageBuilder.append(detection.getText()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener((dialogInterface) -> {
                    barcodeAnalyzer.resume();
                    api.resetScanningState();
                })
                .setPositiveButton("OK", null)
                .show();
    }
    // ...
}
```

## 高度バーコードスキャン
高度バーコードスキャンは、ボヤケや照明条件の悪い画像からもバーコードを認識できるようにするための、より強力なバーコード認識モデルです。
現在はまだ実験的ですので、使用するには `availableModelsWithExperimental` メソッドを使用してモデルを取得し、`useModel` メソッドで有効化してください。
モデル名は、`barcode_advanced` です。

現在認識できるバーコード種類は以下の通りです。
- ITF-14とITF-16
- EAN13、JAN、UPCA
- Code128

以下の設定が可能である以外は，通常のバーコードスキャンと同様に使用できます．
```java
ModelSettings modelSettings = new ModelSettings();
AdvancedBarcodeRecognizerSettings advancedSettings = new AdvancedBarcodeRecognizerSettings();
advancedSettings.setEnforceChecksum(true);
advancedSettings.setCandidateThreshold(0.45f);
advancedSettings.setAcceptanceThreshold(0.65f);
advancedSettings.setAdvancedOnly(false);
advancedSettings.setEan13Hint(Ean13Type.EAN13);
modelSettings.setAdvancedBarcodeRecognizerSettings(advancedSettings);
edgeVisionAPI.useModel("barcode_advanced", modelSettings);
```
