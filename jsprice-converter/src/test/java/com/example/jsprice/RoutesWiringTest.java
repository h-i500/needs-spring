package com.example.jsprice;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith  // context.start() までRoute起動しない
class RoutesWiringTest {

  @Autowired CamelContext context;
  @Autowired ProducerTemplate template;

  @Test
  void manualRun_goesThrough_runJob_andProducesCsv() throws Exception {
    // ★ 実ルートIDに合わせる（ログにある jsprice-runJob）
    AdviceWith.adviceWith(context, "jsprice-runJob", a -> {
      // HTTP(HTTPS) をモックへ（toD でも確実に捕まえる）
      a.interceptSendToEndpoint("http*")
        .skipSendToOriginalEndpoint()
        .to("mock:http");
      a.interceptSendToEndpoint("https*")
        .skipSendToOriginalEndpoint()
        .to("mock:http");

      // file 出力もモックへ
      a.interceptSendToEndpoint("file*")
        .skipSendToOriginalEndpoint()
        .to("mock:file");
    });

    // モックHTTPが返す最小PDF
    byte[] pdf = MinimalPdfBytes.sample();
    MockEndpoint http = context.getEndpoint("mock:http", MockEndpoint.class);
    http.whenAnyExchangeReceived(e -> e.getIn().setBody(pdf));

    MockEndpoint file = context.getEndpoint("mock:file", MockEndpoint.class);
    file.expectedMessageCount(1);

    context.start();

    // /run と同じ入口（互換ルート）を叩く
    template.sendBody("direct:run", null);

    file.assertIsSatisfied();

    String csv = file.getExchanges().get(0).getIn().getBody(String.class);
    assertTrue(csv.startsWith("brand,maturity_date,coupon_pct,price_jpy"));
    assertTrue(csv.contains("JGB-30Y"));
  }

  // ---- 最小PDFを手元で生成するヘルパ ----
  static class MinimalPdfBytes {
    static byte[] sample() {
      try (PDDocument doc = new PDDocument()) {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
          int y = 780;

          cs.beginText();
          cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
          cs.newLineAtOffset(50, y);
          cs.showText("JS PRICE (TEST)");
          cs.endText();
          y -= 20;

          cs.beginText();
          cs.setFont(PDType1Font.HELVETICA, 12);
          cs.newLineAtOffset(50, y);
          cs.showText("As of 2025/06/30");
          cs.endText();
          y -= 24;

          String[] rows = {
              "JGB-30Y 2033/3/20 1.1 99.2936",
              "JGB-40Y 2057/3/20 0.9 59.8393"
          };
          for (String r : rows) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 11);
            cs.newLineAtOffset(50, y);
            cs.showText(r);
            cs.endText();
            y -= 16;
          }
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos);
        return bos.toByteArray();
      } catch (Exception e) {
        // 失敗することは想定しないが、念のため空PDF相当
        return new byte[0];
      }
    }
  }
}
