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
