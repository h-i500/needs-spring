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

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
class SchedulerRouteTest {

  @Autowired CamelContext context;
  @Autowired ProducerTemplate template;

  @Test
  void quartzRoute_invokes_runJob() throws Exception {
    // Quartz ルートの routeId に合わせる（例: "weeklyMonday10"）
    AdviceWith.adviceWith(context, "weeklyMonday10", a -> {
      a.replaceFromWith("direct:tick");
      a.weaveByToUri("direct:runJob").replace().to("mock:runJob");
    });

    MockEndpoint runJob = context.getEndpoint("mock:runJob", MockEndpoint.class);
    runJob.expectedMessageCount(1);

    context.start();
    template.sendBody("direct:tick", null);

    runJob.assertIsSatisfied();
  }
}
