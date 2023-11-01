## バーコードスキャン

EdgeOCR ではバーコードのスキャンも可能です。

`app/src/main/java/com/nefrock/edgeocr_example/barcode` に実装例がありますので、ご参考にしてください。

`app/src/main/java/com/nefrock/edgeocr_example/barcode/BarcodeScannerActivity.java`でapiの初期化とバーコードフォーマット毎の確定までの読み取り回数を設定しています。
サンプルではQRコードのみ5回読み取った後、結果を確定するように設定しています。
それ以外のバーコードフォーマットは読み取り回数を指定していないため、デフォルトである3回の読み取り後に結果を確定します。

```Java
public class BarcodeScannerActivity extends AppCompatActivity {
    // ...
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ...
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            api.setBarcodesNToConfirm(Collections.singletonList(new Pair<>(BarcodeFormat.QRCode, 5)));
            showDialog = getIntent().getBooleanExtra("show_dialog", false);
            barcodeAnalyzer = new BarcodeAnalyzer(api);
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }
        // ...
    }
    // ...
}
```

`app/src/main/java/com/nefrock/edgeocr_example/barcode/BarcodeAnalyzer.java` においてバーコードスキャンを実行しています。

OCR の場合と基本的には同じですが、バーコードを読む場合は `api.scanTexts` の代わりに `api.scanBarcodes` を呼び出してください。また、`api.scanBarcodes` の第2引数には、`BarcodeScanOption` を用いて読みたいバーコードのフォーマットを指定します。こちらのサンプルではすべてのバーコードのフォーマットを読み取るようにしていますが、リストで指定することで、複数のフォーマットを指定することも可能です。

また、`app/src/main/java/com/nefrock/edgeocr_example/barcode/BarcodeScannerActivity.java`で設定した読み取り回数を超えた場合は、`detection.getStatus()`で`ScanConfirmationStatus.Confirmed`が返ります。
本サンプルでは読み取り回数を超えたバーコードのみを結果として表示しています。

`showDialog`でスキャン結果の表示後、`api.resetScanningState`を呼び出すことで、APIのスキャン状況をリセットしています。これにより、バーコードの確定までの読み取り回数をリセットすることができます。

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
            List<Detection<Barcode>> targetDetections = new ArrayList<>();
            for (Detection<Barcode> detection : scanResult.getBarcodeDetections()) {
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
    private void showDialog(List<Detection<Barcode>> detections) {
        barcodeAnalyzer.stop();
        StringBuilder messageBuilder = new StringBuilder();
        for (Detection<Barcode> detection : detections) {
            messageBuilder.append(detection.getScanObject().getText()).append("\n");
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
