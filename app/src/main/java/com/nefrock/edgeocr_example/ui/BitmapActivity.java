package com.nefrock.edgeocr_example.ui;

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
import com.nefrock.edgeocr_example.R;

import java.io.IOException;

public class BitmapActivity extends AppCompatActivity {
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

        BoxesOverlay boxesOverlay = findViewById(R.id.boxesBitmapOverlay);
        ImageView imageView = findViewById(R.id.bitmapImageView);
        try{
            AssetManager assetManager = getAssets();
            this.bitmap = BitmapFactory.decodeStream(assetManager.open("images/sample.bmp"));
            imageView.setImageBitmap(this.bitmap);
        } catch(IOException e){
            Log.e("EdgeOCRExample", "[onCreate] Failed to load image", e);
        }

        api.useModel(model, (ModelInformation modelInformation) -> {
            ScanResult analysisResult = api.scanTextsOneShot(bitmap, 0.5f, 0.5f, 1.0f);
            ConstraintLayout.LayoutParams imageViewLayoutParams = (ConstraintLayout.LayoutParams) imageView.getLayoutParams();
            // Set aspect ratio of imageview to match the image
            imageViewLayoutParams.dimensionRatio = String.format("%d:%d", bitmap.getWidth(), bitmap.getHeight());
            runOnUiThread(() -> {
                imageView.setLayoutParams(imageViewLayoutParams);
                // Once that is applied, set the correct overlay aspect ratio
                imageView.post(() -> {
                    float modelAspectRatio = modelInformation.getAspectRatio();
                    float imageAspectRatio = (float) imageView.getWidth() / (float) imageView.getHeight();
                    ConstraintLayout.LayoutParams boxesOverlayLayoutParams = (ConstraintLayout.LayoutParams) boxesOverlay.getLayoutParams();
                    boxesOverlayLayoutParams.matchConstraintPercentWidth = Math.min(1, modelAspectRatio / imageAspectRatio);
                    boxesOverlayLayoutParams.matchConstraintPercentHeight = Math.min(1, imageAspectRatio / modelAspectRatio);
                    boxesOverlayLayoutParams.horizontalBias = 0.5f;
                    boxesOverlayLayoutParams.verticalBias = 0.5f;
                    runOnUiThread(() -> {
                        boxesOverlay.setLayoutParams(boxesOverlayLayoutParams);
                        // After layout is done, set the boxes
                        boxesOverlay.post(() -> boxesOverlay.setBoxes(analysisResult.getDetections()));
                    });
                });
            });
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
    }
}
