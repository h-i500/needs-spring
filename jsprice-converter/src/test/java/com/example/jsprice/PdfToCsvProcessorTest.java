package com.example.jsprice;

import com.example.jsprice.processor.PdfToCsvProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PdfToCsvProcessorTest {

  @Test
  void extractSimpleTable() throws Exception {
    byte[] pdf = createSimpleAsciiPdf(
        "JS PRICE (TEST)",
        "2025/06/30",
        // ASCII only (brand names simplified)
        "JGB-30Y 2033/3/20 1.1 99.2936",
        "JGB-40Y 2057/3/20 0.9 59.8393"
    );

    PdfToCsvProcessor p = new PdfToCsvProcessor();
    DefaultCamelContext ctx = new DefaultCamelContext();
    Exchange ex = new DefaultExchange(ctx);
    ex.getIn().setBody(pdf);

    p.process(ex);

    String csv = ex.getIn().getBody(String.class);
    assertNotNull(csv);
    
    // 行ごとに分割して検証
    String[] lines = csv.split("\\R");
    assertTrue(lines.length >= 3, "CSV should have header + 2 data rows");

    // 1行目＝ヘッダを厳密一致で確認
    assertEquals("as_of_date,brand,maturity_date,coupon_pct,price_jpy", lines[0]);

    assertTrue(csv.matches("(?s).*^\\s*\"?as_of_date\"?,\"?brand\"?,\"?maturity_date\"?,\"?coupon_pct\"?,\"?price_jpy\"?\\R.*"),
           "CSV header not found:\n" + csv);
    
    // データ行の中身を軽くチェック（自由度を残すなら contains でもOK）
    assertTrue(csv.contains("2025-06-30"));
    assertTrue(csv.contains("2033-03-20"));
    assertTrue(csv.contains("2057-03-20"));

    assertTrue(csv.matches("(?s).*2025-06-30.*"), "as_of_date missing");
    assertTrue(csv.matches("(?s).*2033-03-20.*"), "2033-03-20 missing");
    assertTrue(csv.matches("(?s).*2057-03-20.*"), "2057-03-20 missing");
  }

  private byte[] createSimpleAsciiPdf(String title, String asOf, String... rows) throws Exception {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);

      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        // Title
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
        cs.newLineAtOffset(50, 780);
        cs.showText(title);
        cs.endText();

        // As of
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(50, 760);
        cs.showText("As of " + asOf);
        cs.endText();

        // Data rows (ASCII only)
        int y = 730;
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
    }
  }
}
