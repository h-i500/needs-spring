package com.example.jsprice;

import com.example.jsprice.processor.PdfToCsvProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
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
  void extractSimpleTable_withoutAsOfDateColumn() throws Exception {
    byte[] pdf = createSimpleAsciiPdf(
        "JS PRICE (TEST)",        // タイトル（無視される）
        "As of 2025/06/30",       // 日付（現実装では使わない）
        // シンプルな 1 行完結データ（ASCII）
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

    String[] lines = csv.split("\\R");
    // ヘッダ + 2 データ行
    assertEquals(3, lines.length, "CSV should have header + 2 data rows");

    // ヘッダの厳密一致
    assertEquals("brand,maturity_date,coupon_pct,price_jpy", lines[0]);

    // 1 行目の中身検証
    // brand, maturity_date(正規化), coupon, price の順
    assertEquals("JGB-30Y,2033-03-20,1.1,99.2936", lines[1]);

    // 2 行目の中身検証
    assertEquals("JGB-40Y,2057-03-20,0.9,59.8393", lines[2]);
  }

  @Test
  void ignoresNoiseAndJoinsTwoLines() throws Exception {
    byte[] pdf = createSimpleAsciiPdf(
        "NIKKEI NEEDS JS PRICE",  // ノイズ（無視される想定）
        "Page 1",                 // ノイズ（無視される想定）
        // 途中で改行されてしまったケースを想定（brand と残りが別行）
        "JGB-20Y",
        "2031/12/20 1.7 103.8442",
        // もう 1 つ通常行
        "JGB-10Y 2035/3/20 1.4 99.744"
    );

    PdfToCsvProcessor p = new PdfToCsvProcessor();
    DefaultCamelContext ctx = new DefaultCamelContext();
    Exchange ex = new DefaultExchange(ctx);
    ex.getIn().setBody(pdf);

    p.process(ex);

    String csv = ex.getIn().getBody(String.class);
    assertNotNull(csv);

    String[] lines = csv.split("\\R");
    // ヘッダ + 2 データ行（結合で 1 行として拾えること）
    assertEquals(3, lines.length, "CSV should have header + 2 joined data rows");
    assertEquals("brand,maturity_date,coupon_pct,price_jpy", lines[0]);

    // 結合された 1 行目
    assertEquals("JGB-20Y,2031-12-20,1.7,103.8442", lines[1]);

    // 通常の 1 行
    assertEquals("JGB-10Y,2035-03-20,1.4,99.744", lines[2]);
  }

  // --- helper ---

  /**
   * タイトル行・As of 行・データ行（任意件）を、縦方向に並べた単純な PDF を生成。
   * データ行はそのまま 1 行ずつ描画します（ASCII のみ）。
   */
  private byte[] createSimpleAsciiPdf(String title, String asOf, String... rows) throws Exception {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);

      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        int y = 780;

        // Title
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
        cs.newLineAtOffset(50, y);
        cs.showText(title);
        cs.endText();
        y -= 20;

        // As of（現実装では使っていないが、ノイズ除去確認用に入れておく）
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 12);
        cs.newLineAtOffset(50, y);
        cs.showText(asOf);
        cs.endText();
        y -= 24;

        // Data rows
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
