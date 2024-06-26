## Bitmap実装
### Text
APIへの入力画像として、ImageProxyではなくBitmapを用いた実装の例が ``app/src/main/java/com/nefrock/edgeocr_example/text_bitmap` になります。

AssetManager を用いて、`assets/images/sample.bmp` を読み出し API に渡しています。

bitmap を引数に `api.scan` 呼び出した場合、同期的にOCR結果が返却されます。

```Java
public class TextBitmapActivity extends AppCompatActivity {
    // ...

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ...

        CameraOverlay overlay = findViewById(R.id.camera_overlay);
        ImageView imageView = findViewById(R.id.bitmap_image_view);
        try{
            AssetManager assetManager = getAssets();
            this.bitmap = BitmapFactory.decodeStream(assetManager.open("images/sample.bmp"));
            imageView.setImageBitmap(this.bitmap);
        } catch(IOException e){
            Log.e("EdgeOCRExample", "[onCreate] Failed to load image", e);
        }

        ScanResult scanResult;
        try {
            scanResult = api.scan(bitmap);
        } catch (EdgeError edgeError) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to scan image", edgeError);
            return;
        }

        ConstraintLayout.LayoutParams imageViewLayoutParams = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
        // Set aspect ratio of imageview to match the image
        imageViewLayoutParams.dimensionRatio = String.format("%d:%d", bitmap.getWidth(), bitmap.getHeight());
        float modelAspectRatio = getIntent().getFloatExtra("model_aspect_ratio", 1.0f);
        overlay.setAspectRatio(modelAspectRatio);
        imageView.setLayoutParams(imageViewLayoutParams);
        overlay.setBoxes(scanResult.getTextDetections());
    }

    // ...
}
```

### Barcode
Bitmap画像からバーコードを読み取るサンプルが `app/src/main/java/com/nefrock/edgeocr_example/barcode_bitmap` になります。

AssetManager を用いて、`assets/images/sample_barcode.bmp` を読み出し API に渡しています。

bitmap を引数に `api.scan` 呼び出した場合、同期的にOCR結果が返却されます。ただ、
`useModel`でバーコードの読めるモデルを指定する必要があります。

```Java
public class BarcodeBitmapActivity extends AppCompatActivity {
    // ...
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bitmap);
        // Initialize EdgeOCR
        EdgeVisionAPI api;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }

        CameraOverlay overlay = findViewById(R.id.camera_overlay);
        ImageView imageView = findViewById(R.id.bitmap_image_view);
        try{
            AssetManager assetManager = getAssets();
            this.bitmap = BitmapFactory.decodeStream(assetManager.open("images/sample_barcode.bmp"));
            imageView.setImageBitmap(this.bitmap);
        } catch(IOException e){
            Log.e("EdgeOCRExample", "[onCreate] Failed to load image", e);
        }

        ScanResult scanResult;
        try {
            api.resetScanningState();
            scanResult = api.scan(bitmap);
        } catch (EdgeError edgeError) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to scan image", edgeError);
            return;
        }

        ConstraintLayout.LayoutParams imageViewLayoutParams = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
        // Set aspect ratio of imageview to match the image
        imageViewLayoutParams.dimensionRatio = String.format("%d:%d", bitmap.getWidth(), bitmap.getHeight());
        runOnUiThread(() -> {
            imageView.setLayoutParams(imageViewLayoutParams);
            overlay.setBoxes(scanResult.getBarcodeDetections());
        });
    }

    // ...
}
```
