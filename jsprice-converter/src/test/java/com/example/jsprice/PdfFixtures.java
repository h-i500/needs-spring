package com.example.jsprice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;

public final class PdfFixtures {
  private PdfFixtures() {}

  public static byte[] createSimpleAsciiPdf(String title, String asOf, String... rows) throws Exception {
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

        // As of
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
