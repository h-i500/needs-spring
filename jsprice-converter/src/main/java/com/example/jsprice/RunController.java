package com.example.jsprice;

import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RunController {

  private final ProducerTemplate template;

  // Lombokなしのコンストラクタ注入
  public RunController(ProducerTemplate template) {
    this.template = template;
  }

  // ワンショット実行: curl -X POST http://localhost:10080/run
  @PostMapping("/run")
  public ResponseEntity<String> run() {
    template.sendBody("direct:run", null);
    return ResponseEntity.ok("OK");
  }
}
