package com.example.jsprice.processor;

import com.opencsv.CSVWriter;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JS Price PDF から「銘柄名, 償還日, 表面利率, 債券標準価格」を抽出してCSV化
 * - ページ見出し（償還日/表面利率/債券標準価格/銘柄名…）は除去
 * - 1行に複数銘柄が連結されたケースを銘柄の“きっかけ語”で分割
 * - 表面利率が空欄の銘柄も許容（空文字で出力）
 * 出力列: brand, maturity_date, coupon_pct, price_jpy
 */
public class PdfToCsvProcessor implements Processor {

  /** ページ見出し（各ページ上部の項目名）を除去 */
  private static final Pattern HEADER_CHUNK = Pattern.compile(
      "償還日\\s+表面利率\\s+債券標準価格\\s+銘柄名\\s+（年・月・日）\\s+（％）\\s+（円）\\s*"
  );

  /** 銘柄の“きっかけ語”直前に改行を挿入して、1行複数銘柄を分割 */
  private static final Pattern BRAND_CUE = Pattern.compile(
      "(?=第[０-９0-9]+回\\s+利付国債（)|" +                 // 第●回 利付国債（…）
      "(?=分離利息国債（)|" +                                 // 分離利息国債（…）
      "(?=第[０-９0-9]+回\\s+物価連動国債（)|" +             // 第●回 物価連動国債（…）
      "(?=第[０-９0-9]+回\\s+クライメート・トランジション利付国債（)" // 第●回 クライメート・トランジション…
  );

  /**
   * 行パターン
   * brand  ... 改行を跨がない任意文字（できるだけ短く）
   * date   ... yyyy/m/d（空白混在OK）
   * coupon ... 任意（無い場合あり）
   * price  ... 必須
   * マルチラインで ^/$ を行頭/行末にする
   */
  private static final Pattern ROW = Pattern.compile(
      "^(?<brand>.+?)\\s+" +
      "(?<date>\\d{4}\\s*/\\s*\\d{1,2}\\s*/\\s*\\d{1,2})" +
      "(?:\\s+(?<coupon>[\\d.,]+))?\\s+" +
      "(?<price>[\\d.,]+)\\s*$",
      Pattern.MULTILINE
  );

  @Override
  public void process(Exchange exchange) throws Exception {
    byte[] pdf = exchange.getIn().getBody(byte[].class);
    if (pdf == null || pdf.length == 0) {
      throw new IllegalArgumentException("No PDF content in exchange body.");
    }

    String text = extractText(pdf);
    text = cleanup(text); // 見出し除去・分割など前処理

    List<String[]> rows = extractRows(text);
    // rows = uniqueRows(rows);  // 重複除去したい場合はコメントアウト解除
    String csv = toCsvString(rows);
    exchange.getIn().setBody(csv);
    exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/csv; charset=UTF-8");
  }

  /** PDF -> テキスト */
  private String extractText(byte[] pdf) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(doc);
    }
  }

  /** テキスト前処理：見出し除去、空白正規化、銘柄の直前に改行を挿入して分割を安定化 */
  private String cleanup(String text) {
    String x = text;

    // 見出し塊を除去（複数ページ想定）
    x = HEADER_CHUNK.matcher(x).replaceAll("");

    // 全角スペース/タブ -> 半角、連続空白を1つに
    x = x.replace('\u3000', ' ').replace('\t', ' ');
    x = x.replaceAll(" +", " ");

    // 銘柄の“きっかけ語”直前に改行を「挿入」（消さない！）
    x = BRAND_CUE.matcher(x).replaceAll("\n$0");

    // 連続改行の整理
    x = x.replaceAll("\\n{2,}", "\n").trim();

    return x;
  }

  /** 全文に対して find() で行を拾う（行単位読み出しはしない） */
  private List<String[]> extractRows(String text) {
    List<String[]> out = new ArrayList<>();
    out.add(new String[]{"brand", "maturity_date", "coupon_pct", "price_jpy"});

    Matcher m = ROW.matcher(text);
    while (m.find()) {
      String brand  = normalizeBrand(m.group("brand"));
      String date   = normalizeDate(m.group("date"));
      String coupon = normalizeDecimalOrEmpty(m.group("coupon")); // 空欄許容
      String price  = normalizeDecimalOrEmpty(m.group("price"));

      // 念のため：万が一見出し残骸がbrand先頭にあれば落とす
      if (brand.contains("償還日") && brand.contains("表面利率")) {
        brand = brand.replaceFirst(".*?（円）\\s*", "");
      }

      out.add(new String[]{brand, date, coupon, price});
    }
    return out;
  }

  /** CSV 文字列化（LF終端） */
  private String toCsvString(List<String[]> rows) throws IOException {
    StringWriter sw = new StringWriter();
    try (CSVWriter writer = new CSVWriter(
        sw,
        CSVWriter.DEFAULT_SEPARATOR,
        CSVWriter.DEFAULT_QUOTE_CHARACTER,
        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
        "\n")) {
      writer.writeAll(rows, false);
    }
    return sw.toString();
  }

  /* ---------- Normalizers ---------- */

  private String normalizeBrand(String s) {
    if (s == null) return "";
    String x = s.trim();
    x = x.replaceAll("\\s+", " ");
    return x;
  }

  private String normalizeDate(String yyyyMd) {
    String compact = yyyyMd.replaceAll("\\s+", ""); // "2033 / 3 / 20" → "2033/3/20"
    String[] p = compact.split("/");
    return String.format("%04d-%02d-%02d",
        Integer.parseInt(p[0]),
        Integer.parseInt(p[1]),
        Integer.parseInt(p[2]));
  }

  /** 数値 or 空文字（null/空はそのまま空） */
  private String normalizeDecimalOrEmpty(String s) {
    if (s == null || s.isBlank()) return "";
    String z = s.replace(",", "");
    return new BigDecimal(z).stripTrailingZeros().toPlainString();
  }

  // /** 重複除去したい場合はコメントアウト解除 **/
  // private List<String[]> uniqueRows(List<String[]> rows) {
  //   if (rows.isEmpty()) return rows;
  //   List<String[]> out = new ArrayList<>();
  //   out.add(rows.get(0)); // ヘッダはそのまま

  //   java.util.HashSet<String> seen = new java.util.HashSet<>();
  //   for (int i = 1; i < rows.size(); i++) {
  //     String[] r = rows.get(i);
  //     // brand, date, coupon, price の完全一致でユニーク化
  //     String key = String.join("\u0001", r);
  //     if (seen.add(key)) out.add(r);
  //   }
  //   return out;
  // }
}
