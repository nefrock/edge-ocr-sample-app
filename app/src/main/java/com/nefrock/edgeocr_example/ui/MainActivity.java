package com.nefrock.edgeocr_example.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;

import com.nefrock.edgeocr.api.NefrockLicenseAPI;

import com.nefrock.edgeocr_example.R;

@ExperimentalCamera2Interop public class MainActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.simple_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), SimpleTextScannerActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.free_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), TextScannerActivity.class);
            intent.putExtra("analyser_type", "free_style");
            intent.putExtra("show_dialog", false);
            startActivity(intent);
        });

        findViewById(R.id.whitelist_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), TextScannerActivity.class);
            intent.putExtra("analyser_type", "whitelist");
            intent.putExtra("show_dialog", true);
            startActivity(intent);
        });

        findViewById(R.id.reg_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), TextScannerActivity.class);
            intent.putExtra("analyser_type", "regex");
            intent.putExtra("show_dialog", true);
            startActivity(intent);
        });

        findViewById(R.id.ed_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), TextScannerActivity.class);
            intent.putExtra("analyser_type", "edit_distance");
            intent.putExtra("show_dialog", true);
            startActivity(intent);
        });

        findViewById(R.id.barcode_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeScannerActivity.class);
            intent.putExtra("show_dialog", true);
            startActivity(intent);
        });


        NefrockLicenseAPI licenseAPI = new NefrockLicenseAPI.Builder(this)
            .withLicenseKey(<your key>)
            .build();

        Button activationButton = findViewById(R.id.activation_button);
        activationButton.setText("アクティベーション状態を確認");
        activationButton.setEnabled(false);

        licenseAPI.isActivated(
            license -> new Handler(
                    Looper.getMainLooper()).post(() -> activationButton.setText("アクティベート済み")),
            edgeError -> new Handler(Looper.getMainLooper()).post(() -> {
                activationButton.setText("アクティベート");
                activationButton.setEnabled(true);
            })
        );
        activationButton.setOnClickListener(view -> licenseAPI.activate(
            license -> {
                // Make Toast in Main Thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    activationButton.setText("アクティベート済み");
                    activationButton.setEnabled(false);
                    Toast.makeText(
                        getApplicationContext(), "アクティベーション完了", Toast.LENGTH_LONG).show();
                });
            },
            edgeError -> {
                // Make Toast in Main Thread
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                    getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG).show());
            }
        ));
    }
}
