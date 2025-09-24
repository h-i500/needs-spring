package com.example.jsprice.config;

import com.example.jsprice.processor.PdfToCsvProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Routes extends RouteBuilder {

  @Value("${app.sourceUrl}")
  String sourceUrl;

  @Value("${app.output.dir:data/output}")
  String outputDir;

  @Value("${app.output.filename:jsprice_20250630.csv}")
  String outputFileName;

  @Override
  public void configure() {

    // 例外時はエラーファイル退避＆ログ
    onException(Exception.class)
      .handled(true)
      .log("Processing failed: ${exception.message}")
      // 失敗した入力を退避（PDF本文やCSV本文がBodyに入っている段階で拾います）
      .process(e -> {
        Object body = e.getIn().getBody();
        if (body != null) {
          e.getIn().setHeader(Exchange.FILE_NAME, e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class) != null
              ? "failed_" + System.currentTimeMillis() + ".bin"
              : "failed.bin");
        }
      })
      .to("file:data/error")
      .setBody(simple("ERROR: ${exception.message}"))
      .to("log:jsprice-error?level=ERROR");

    // 手動起動ルート（RunController から叩く）
    from("direct:run")
      .routeId("jsprice-extract")
      .setHeader("outputDir", simple(outputDir))
      .setHeader("outputFileName", simple(outputFileName))
      .log("Downloading PDF from: " + sourceUrl)
      // camel-http コンポーネントを使用（httpsスキームでOK）
      .toD("{{app.sourceUrl}}?bridgeEndpoint=true")
      .process(new PdfToCsvProcessor())
      .log("Writing CSV to: ${header.outputDir}/${header.outputFileName}")
      .toD("file:${header.outputDir}?fileName=${header.outputFileName}")
      .log("Done.");
  }
}
