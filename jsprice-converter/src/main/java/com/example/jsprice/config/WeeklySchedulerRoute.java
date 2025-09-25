package com.example.jsprice.config;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class WeeklySchedulerRoute extends RouteBuilder {

  @Override
  public void configure() {
    // cron: 秒 分 時 日 月 曜日
    // 毎週月曜 10:00（日本時間）
    from("quartz://weekly/monday10"
        + "?cron=0+0+10+?+*+MON"
        + "&trigger.timeZone=Asia/Tokyo")
      .routeId("weeklyMonday10")
      .log("Quartz fired: Monday 10:00 Asia/Tokyo")
      .to("direct:runJob");
  }
}
