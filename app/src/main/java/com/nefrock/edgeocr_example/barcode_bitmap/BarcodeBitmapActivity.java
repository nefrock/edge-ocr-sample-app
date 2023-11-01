package com.nefrock.edgeocr_example.barcode_bitmap;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;

import com.nefrock.edgeocr.api.BarcodeScanOption;
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.BarcodeFormat;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;

import java.io.IOException;
import java.util.Collections;

public class BarcodeBitmapActivity extends AppCompatActivity {
    private Bitmap bitmap;
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
            api.setBarcodesNToConfirm(Collections.singletonList(new Pair(BarcodeFormat.Any, 1)));
            scanResult = api.scanBarcodes(bitmap, new BarcodeScanOption(Collections.singletonList(BarcodeFormat.Any)));
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
}
