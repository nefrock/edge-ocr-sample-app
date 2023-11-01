## 読めない画像のフィードバック
弊社のEdgeOCRは、日々の学習と進化を続け、最高の性能を提供するよう努力しています。現場で読み取れない画像やテキストに出会った場合、ぜひそれらの情報を弊社のサーバーにフィードバックしていただけると幸いです。お客様からのフィードバックを受けて、我々はより優れたOCRエンジンを開発し、次回のリリースで読み取れなかったテキストや画像に対処する可能性が高まります。皆様の貴重なフィードバックをお待ちしております。

フィードバックを送信するには `reportImage` というメソッドを使用します。

```Java
ScanResult reportImage(
        @NonNull ImageProxy image,
        @NonNull String userMessage) throws EdgeError;
```
メソッドを実行すると画像に対してスキャンが行われ、画像と結果が弊社のサーバーに送信されます。
`userMessage` は任意記述になります。画像を撮影した状況などを記述していただけると助かります。

`app/src/main/java/com/nefrock/edgeocr_example/report/ReportScannerActivity.java`
に、ボタンを押すと現在の画面をフィードバックする機能を実装しています。ご自身のアプリに組み込む場合は、こちらのコードを参考にしてください。

```Java
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ...
        Button reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(
            (View v) -> {
                freeStyleTextAnalyzer.stop();
                api.resetScanningState();
                // Capture the image
                imageCapture.takePicture(
                        analysisExecutor,
                        new ImageCapture.OnImageCapturedCallback() {
                            @Override
                            public void onCaptureSuccess(@NonNull ImageProxy image) {
                                try {
                                    api.reportImage(image, "test");
                                } catch (Exception e) {
                                    Log.e("EdgeOCRExample", Log.getStackTraceString(e));
                                } finally {
                                    image.close();
                                }
                                freeStyleTextAnalyzer.resume();
                            }

                            @Override
                            public void onError(@NonNull ImageCaptureException exception) {
                                Log.e("EdgeOCRExample", "[onCaptureSuccess] Failed to capture image", exception);
                                freeStyleTextAnalyzer.resume();
                            }
                        });
            });
        // ...
    }
```
