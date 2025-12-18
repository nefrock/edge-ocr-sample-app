## アクティベーション

SDK のスキャン機能を使う前に、SDK を使用するデバイスでライセンスのアクティベーションを行う必要があります。
アクティベーションはそのデバイスで初めて SDK を使うときのみ必要です。
アクティベーションはオンライン環境で行う必要があります。

> [!WARNING]
> デバイスのファクトリーリセットを行うと、再度のアクティベーションが必要になり、別デバイスとして登録されますのでご注意ください。

アクティベーションの実行、アクティベーション状態の確認を行うために、 `NefrockLicenseAPI` を用います。
```Java
NefrockLicenseAPI licenseAPI = new NefrockLicenseAPI.Builder(<Context>)
    .withLicenseKey(<your key>)
    .build();
```
`<Context>` には、アプリの `Context` を定義してください。
`<your key>` の部分にはライセンスキーを入れてください。

アクティベーションを行うには、`activate` メソッドを呼び出します。
```Java
ListenableFuture<License> activate();
```
返却される `ListenableFuture` は、デバイスのライセンス情報を含む `License` オブジェクトを返します。
必要に応じて以下のようにコールバックを登録して、ライセンス情報を受け取ることができます。
```Java
ListenableFuture<License> future = licenseAPI.refreshLicense();
Futures.addCallback(
    future,
    new FutureCallback<License>() {
        @Override
        public void onSuccess(License license) {
            // ライセンス情報の更新に成功した場合の処理
        }

        @Override
        public void onFailure(Throwable t) {
            // ライセンス情報の更新に失敗した場合の処理
        }
    },
    Runnable::run
);
```

一度アクティベーションを行うと、次回以降はアクティベーションを行う必要はありません。
ライセンス情報はファイルに保存されますので、アプリを再起動してもアクティベーションを行う必要はありません。

アクティベーション状態の確認は、`isActivated` で行うことができます。
```Java
ListenableFuture<License> isActivated();
```
ライセンスファイルが存在するか確認を行います。
ファイルが見つからない場合、サーバーに非同期で問い合わせを行います。
デバイスがアクティベーション済みの場合ライセンスファイルが生成されます。
この関数を呼ぶことにより、新しいデバイスとして登録されることはありません。
`activate` メソッドと同様に、`ListenableFuture` を用いてコールバックを登録することができます。

このアクティベーションフローの例は`app/src/main/java/com/nefrock/edgeocr_example/MainActivity.java`に定義されていますので、ご参考にしてください。

実際にお試し頂く場合は、サンプルアプリを実機にインストールし、起動する最初の画面（メイン画面）で一番下の緑色のアクティベーションボタンを押してください。

「メイン画面」
<br/>
<img src="images/main-activity.png" height="300px">
<br/>
<br/>

アクティベーション済みの場合、アクティベーションボタンには「アクティベーション済み」と表示されます。

### ライセンス情報の更新
ライセンス情報をサーバーから最新の情報に更新するには、`refreshLicense` メソッドを呼び出します。
```Java
ListenableFuture<License> refreshLicense();
```
`activate` メソッドと同様に、`ListenableFuture` を用いてコールバックを登録することができます。


### アクティベーションの解除
アクティベーションを解除するには、`deactivate` メソッドを呼び出します。
```Java
ListenableFuture<Void> deactivate();
```
アクティベーションを解除すると、デバイスに保存されているライセンス情報が削除され、サーバー側の登録も解除されます。
認証の解除終了、またはエラーに反応するために、`ListenableFuture` を用いてコールバックを登録することができます。
