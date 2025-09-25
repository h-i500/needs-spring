package com.example.jsprice.config;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 検証用: 毎分 direct:runJob を叩く。
 * app.debug.everyMinute=true のときだけ登録される。
 */
@Component
@ConditionalOnProperty(name = "app.debug.everyMinute", havingValue = "true")
public class DebugEveryMinuteRoute extends RouteBuilder {
  @Override
  public void configure() {
    // cron: 秒 分 時 日 月 曜日（スペースは + でエスケープ）
    from("quartz://debug/everyMinute"
        + "?cron=0+*/1+*+?+*+*"
        + "&trigger.timeZone=Asia/Tokyo")
      .routeId("debugEveryMinute")
      .log("DEBUG quartz fired: every minute (Asia/Tokyo)")
      .to("direct:runJob");
  }
}
