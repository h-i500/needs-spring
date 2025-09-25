package com.example.post.config;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Routes extends RouteBuilder {

  @Value("${app.inDir:/data/output}")
  String inDir;

  @Value("${app.outDir:/data/txt}")
  String outDir;

  @Value("${app.replaceCommaWithTab:false}")
  boolean replaceCommaWithTab;

  @Override
  public void configure() {

    // 失敗したファイルはエラー置き場へ
    onException(Exception.class)
      .handled(true)
      .log("TXT変換失敗: ${exception.message} for ${header.CamelFileName}")
      .toD("file:" + outDir + "/.error?fileName=${date:now:yyyyMMddHHmmss}-${file:name}");

    // /data/output を監視して *.csv を拾う
    // - readLock=changed: 書き込み中のファイルを掴まない
    // - idempotent=true: 同一ファイルを再処理しない（デフォルトレポジトリはメモリ）
    // - move=.done/${file:name}: 変換成功後は入力側を .done へ退避
    from("file:" + inDir
        + "?include=.*\\.csv"
        + "&readLock=changed"
        + "&readLockMinAge=1000"      // 1秒間変更無しを確認してから取り込む（推奨）
        + "&readLockCheckInterval=500"// readLock の再チェック間隔
        + "&idempotent=true"
        + "&delay=5000"               // 5秒ごとにポーリング
        + "&initialDelay=2000"        // 起動直後は2秒待ってから開始
        + "&move=.done/${file:name}")
      .routeId("csv-to-txt")
      .log("CSV検出: ${file:absolute.path}")
      // 必要ならここで変換（カンマ→タブ）
      .process(e -> {
        String body = e.getMessage().getBody(String.class);
        if (replaceCommaWithTab) {
          body = body.replace(",", "\t");
        }
        e.getMessage().setBody(body);
      })
      // 出力ファイル名を .txt に
      .setHeader(Exchange.FILE_NAME, simple("${file:name.noext}.txt"))
      .toD("file:" + outDir)
      .log("TXT出力: " + outDir + "/${header.CamelFileName}");
  }
}
