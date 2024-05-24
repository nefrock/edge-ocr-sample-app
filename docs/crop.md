## 任意の範囲をスキャンする

画像の中心部分以外を自由に切り取りスキャンすることも可能です。
`app/src/main/java/com/nefrock/edgeocr_example/crop` に実装例がありますので、ご参考にしてください。

### 実装
`app/src/main/java/com/nefrock/edgeocr_example/crop/CropFreeStyleTextAnalyzer.java` において、切り取り範囲を指定したスキャンを実行しています。
```Java
ScanOptions options = new ScanOptions();
options.setScanMode(ScanOptions.ScanMode.Default);
options.setCropRect(new CropRect(horizontalBias, verticalBias, width, height));
api.scan(image, options);
```

`api.scan` メソッドに`horizontalBias`、`verticalBias`、`width`, `height`を渡してください。これらの引数を指定することで、画像の任意の部分を切り取ってスキャンすることができます。

#### width, height
`width` と `height` は入力画像の元々の幅と高さを1として、クロップ範囲を画像の幅と高さをそれぞれ0.0から1.0の範囲で設定します。

#### horizontalBias, verticalBias
`horizontalBias`と`verticalBias`は、切り取られる範囲の横方向と縦方向の位置を0.0から1.0の範囲で設定します。0.0の場合は切り取られる範囲の左端（上端）が画像の左端（上端）に、1.0の場合は切り取られる範囲の右端（下端）が画像の右端（下端）になります。0.5の場合は画像の中央になります。
従って、`width`（`height`）が1.0に設定されている場合は、`horizontalBias`（`verticalBias`）が無視されます。
