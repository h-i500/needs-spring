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
      .process(e -> {
        Object body = e.getIn().getBody();
        if (body != null) {
          e.getIn().setHeader(Exchange.FILE_NAME,
              "failed_" + System.currentTimeMillis() + ".bin");
        }
      })
      .to("file:data/error")
      .setBody(simple("ERROR: ${exception.message}"))
      .to("log:jsprice-error?level=ERROR");

    // 互換: 既存の direct:run 呼び出しがあれば runJob にフォワード
    from("direct:run")
      .routeId("compat-direct-run")
      .to("direct:runJob");

    // 実処理本体：手動・定期どちらもここを通る
    from("direct:runJob")
      .routeId("jsprice-runJob")
      .setHeader("outputDir", simple(outputDir))
      .setHeader("outputFileName", simple(outputFileName))
      .log("Downloading PDF from: " + sourceUrl)
      .toD("{{app.sourceUrl}}?bridgeEndpoint=true")
      .process(new PdfToCsvProcessor())
      .log("Writing CSV to: ${header.outputDir}/${header.outputFileName}")
      .toD("file:${header.outputDir}?fileName=${header.outputFileName}")
      .log("Done.");
  }
}
