package com.nefrock.edgeocr_example.text_bitmap;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Model;
import com.nefrock.edgeocr.model.ModelInformation;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;

import java.io.IOException;

public class TextBitmapActivity extends AppCompatActivity {
    private Model model;

    private Bitmap bitmap;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bitmap);
        // Initialize EdgeOCR
        EdgeVisionAPI api;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            api.availableModels().stream()
                .filter(m -> m.getUID().equals("model-large"))
                .findAny()
                .map(m -> model = m)
                .orElseThrow(() -> new Exception("Failed to find model"));
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }

        CameraOverlay overlay = findViewById(R.id.camera_overlay);
        ImageView imageView = findViewById(R.id.bitmap_image_view);
        try{
            AssetManager assetManager = getAssets();
            this.bitmap = BitmapFactory.decodeStream(assetManager.open("images/sample.bmp"));
            imageView.setImageBitmap(this.bitmap);
        } catch(IOException e){
            Log.e("EdgeOCRExample", "[onCreate] Failed to load image", e);
        }

        api.useModel(model, (ModelInformation modelInformation) -> {
            ScanResult scanResult;
            try {
                scanResult = api.scanTexts(bitmap);
            } catch (EdgeError edgeError) {
                Log.e("EdgeOCRExample", "[onCreate] Failed to scan image", edgeError);
                return;
            }

            ConstraintLayout.LayoutParams imageViewLayoutParams = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
            // Set aspect ratio of imageview to match the image
            imageViewLayoutParams.dimensionRatio = String.format("%d:%d", bitmap.getWidth(), bitmap.getHeight());
            runOnUiThread(() -> {
                imageView.setLayoutParams(imageViewLayoutParams);
                float modelAspectRatio = modelInformation.getAspectRatio();
                float imageAspectRatio = (float) imageView.getWidth() / (float) imageView.getHeight();
                overlay.setCrop(
                    0.5f, 0.5f,
                    Math.min(1, modelAspectRatio / imageAspectRatio),
                    Math.min(1, imageAspectRatio / modelAspectRatio));
                overlay.setBoxes(scanResult.getTextDetections());
            });
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
    }
}
