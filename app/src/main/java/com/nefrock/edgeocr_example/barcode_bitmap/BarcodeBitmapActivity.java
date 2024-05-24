package com.nefrock.edgeocr_example.barcode_bitmap;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.nefrock.edgeocr.BarcodeFormat;
import com.nefrock.edgeocr.EdgeError;
import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.ScanOptions;
import com.nefrock.edgeocr.ScanResult;
import com.nefrock.edgeocr.ui.CameraOverlay;

import com.nefrock.edgeocr_example.R;

import java.io.IOException;
import java.util.Collections;

public class BarcodeBitmapActivity extends AppCompatActivity {
    private Bitmap bitmap;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_bitmap);
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
            ScanOptions scanOptions = new ScanOptions();
            scanOptions.setScanMode(ScanOptions.ScanMode.ONE_SHOT);
            scanOptions.setBarcodeFormats(Collections.singletonList(BarcodeFormat.Any));
            scanResult = api.scan(bitmap, scanOptions);
        } catch (EdgeError edgeError) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to scan image", edgeError);
            return;
        }

        ConstraintLayout.LayoutParams imageViewLayoutParams = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
        // Set aspect ratio of imageview to match the image
        overlay.setCrop(0.5f, 0.5f, 1.0f, 1.0f);
        imageView.setLayoutParams(imageViewLayoutParams);
        overlay.setBoxes(scanResult.getBarcodeDetections());
    }
}
