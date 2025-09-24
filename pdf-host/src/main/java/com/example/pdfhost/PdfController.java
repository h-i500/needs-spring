package com.example.pdfhost;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class PdfController {

  @GetMapping("/jsprice/sample")
  public ResponseEntity<byte[]> serveSample() throws IOException {
    var res = new ClassPathResource("sample/jsprice_01_202506.pdf");
    if (!res.exists()) {
      // 置き忘れ対策のメッセージ返却（本来は404でもOK）
      String msg = "Put sample/jsprice_01_202506.pdf under src/main/resources/sample/";
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
          .body(msg.getBytes(StandardCharsets.UTF_8));
    }
    byte[] bytes = StreamUtils.copyToByteArray(res.getInputStream());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(bytes);
  }
}
