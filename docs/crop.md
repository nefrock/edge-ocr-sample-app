## 任意の範囲をスキャンする

画像の中心部分以外を自由に切り取りスキャンすることも可能です。
`app/src/main/java/com/nefrock/edgeocr_example/crop` に実装例がありますので、ご参考にしてください。

### 実装
`app/src/main/java/com/nefrock/edgeocr_example/crop/CropFreeStyleTextAnalyzer.java` において、切り取り範囲を指定したスキャンを実行しています。
```Java
api.scanTexts(
    image,
    new ScanOption(
        ScanOption.ScanMode.SCAN_MODE_TEXTS,
        new CropRect(
                cropLeft,
                cropTop,
                cropWidth,
                cropHeight
        )
    )
);
```

`api.scanText` メソッドに`cropLeft`、`cropTop`、`cropWidth`, `cropHeight`を渡してください。
`cropLeft` は切り取られる範囲の画面左端からの位置を示しています。
`cropTop` は切り取られる範囲の画面上端からの位置を示しています。
`cropWidth` は切り取られる範囲の横幅を示しています。
`cropHeight` は切り取られる範囲の縦幅を示しています。
`cropLeft` と `cropTop` は、画像の左上を原点として横方向と縦方向の位置を0.0から1.0の範囲で設定します。
`cropWidth` と `cropHeight` は入力画像の元々の幅と高さを1として、クロップ範囲を画像の幅と高さをそれぞれ0.0から1.0の範囲で設定します。
`cropLeft`、`cropTop`、`cropWidth`、`cropHeight`を指定することで、画像の任意の部分を切り取ってスキャンすることができます。
