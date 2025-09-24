package com.example.jsprice.processor;

import com.opencsv.CSVWriter;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
// import java.text.ParseException; // ← 削除
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * サンプルPDF（日経NEEDS JS Price）の本文テキストから
 * 「銘柄名  償還日  表面利率  債券標準価格」を抽出してCSVを生成。
 *
 * 出力列: as_of_date, brand, maturity_date, coupon_pct, price_jpy
 */
public class PdfToCsvProcessor implements Processor {

  // 行抽出（銘柄行）
  // 例: "第１０回 利付国債（３０年） 2033/3/20 1.1 99.2936"
//   private static final Pattern ROW = Pattern.compile(
//       "^(?<brand>.+?)\\s+(?<date>\\d{4}/\\d{1,2}/\\d{1,2})\\s+(?<coupon>[\\d\\.]+)\\s+(?<price>[\\d\\.]+)\\s*$");

    // 新: スラッシュ前後の空白許容、カンマ区切り・小数も許容
    private static final Pattern ROW = Pattern.compile(
    "^(?<brand>.+?)\\s+" +
    "(?<date>\\d{4}\\s*/\\s*\\d{1,2}\\s*/\\s*\\d{1,2})\\s+" +
    "(?<coupon>[\\d.,]+)\\s+" +
    "(?<price>[\\d.,]+)\\s*$"
    );

  // PDFヘッダ等からデータ日付（as-of）を拾う: 例 "2025/06/30"
  private static final Pattern AS_OF = Pattern.compile(
        "(20\\d{2})\\s*/\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})"
    );

  @Override
  public void process(Exchange exchange) throws Exception {
    byte[] pdf = exchange.getIn().getBody(byte[].class);
    if (pdf == null || pdf.length == 0) {
      throw new IllegalArgumentException("No PDF content in exchange body.");
    }

    String allText = extractText(pdf);

    // 追加: 抽出結果を10行だけ出す
    int shown = 0;
    try (BufferedReader dbg = new BufferedReader(new StringReader(allText))) {
    String L;
    while ((L = dbg.readLine()) != null && shown < 10) {
        System.out.println("[DBG] " + L);
        shown++;
    }
    }

    String asOfDate = findAsOfDate(allText); // "yyyy-MM-dd" or ""
    List<String[]> rows = extractRows(allText, asOfDate); // ← IOException を上位で拾える

    // CSV 文字列化（UTF-8）
    String csv = toCsvString(rows);

    System.out.println("==== CSV DUMP ====\n" + csv.replace("\r","\\r").replace("\n","\\n\n"));

    exchange.getIn().setBody(csv);
    exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/csv; charset=UTF-8");
  }

  private String extractText(byte[] pdf) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(doc);
    }
  }

  private String findAsOfDate(String text) {
    Matcher m = AS_OF.matcher(text);
    if (m.find()) {
      int y = Integer.parseInt(m.group(1));
      int mo = Integer.parseInt(m.group(2));
      int d = Integer.parseInt(m.group(3));
      return String.format("%04d-%02d-%02d", y, mo, d);
    }
    return "";
  }


  // ★ここを IOException にする（ParseException は不要）
  private List<String[]> extractRows(String text, String asOfDate) throws IOException {
    List<String[]> out = new ArrayList<>();
    out.add(new String[]{"as_of_date", "brand", "maturity_date", "coupon_pct", "price_jpy"});

    try (BufferedReader br = new BufferedReader(new StringReader(text))) {
      String line;
      while ((line = br.readLine()) != null) { // readLine が IOException を投げ得る
        line = normalize(line);
        if (line.isEmpty()) continue;

        Matcher m = ROW.matcher(line);
        if (m.find()) {
          String brand = m.group("brand");
          String date = normalizeDate(m.group("date"));     // yyyy-MM-dd
          String coupon = normalizeDecimal(m.group("coupon"));
          String price = normalizeDecimal(m.group("price"));

          out.add(new String[]{asOfDate, brand, date, coupon, price});
        }
      }
    }
    return out;
  }

  private String toCsvString(List<String[]> rows) throws IOException {
    StringWriter sw = new StringWriter();
    // 行末 \n 固定（OpenCSV の別コンストラクタ）
    try (CSVWriter writer = new CSVWriter(sw, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.DEFAULT_QUOTE_CHARACTER,
                                            CSVWriter.DEFAULT_ESCAPE_CHARACTER, "\n")) {
        writer.writeAll(rows, false);
    }
    return sw.toString();
    }


  /* ---------- Normalizers ---------- */

  private String normalize(String s) {
    if (s == null) return "";
    String x = s.replace('\u3000', ' ').replace('\t', ' ');
    x = x.replaceAll("\\s+", " ").trim();
    // ノイズ判定は今は何もしない（必要ならここで continue 等のロジックを実装）
    // if (x.matches("(?i).*(nikkei|copyright|page|債券標準価格|表面利率).*")) { }
    return x;
  }

  private String normalizeDate(String yyyyMd) {
    String compact = yyyyMd.replaceAll("\\s+", ""); // "2033 / 3 / 20" → "2033/3/20"
    String[] p = compact.split("/");                // ← ここを compact に
    return String.format("%04d-%02d-%02d",
        Integer.parseInt(p[0]),
        Integer.parseInt(p[1]),
        Integer.parseInt(p[2]));
    }

  private String normalizeDecimal(String s) {
    String z = s.replace(",", "");
    BigDecimal bd = new BigDecimal(z);
    return bd.stripTrailingZeros().toPlainString();
  }
}
