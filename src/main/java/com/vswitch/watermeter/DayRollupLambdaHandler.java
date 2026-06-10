package com.vswitch.watermeter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** EventBridge scheduled entry point for end-of-day volume rollup. */
public class DayRollupLambdaHandler implements RequestHandler<ScheduledEvent, String> {

    private static ConfigurableApplicationContext applicationContext;

    private static synchronized ConfigurableApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            applicationContext =
                    new SpringApplicationBuilder(WaterMeterServiceApplication.class)
                            .web(WebApplicationType.NONE)
                            .run();
        }
        return applicationContext;
    }

    @Override
    public String handleRequest(ScheduledEvent event, Context lambdaContext) {
        DayRollupService rollup = getApplicationContext().getBean(DayRollupService.class);
        rollup.runRollup();
        return "OK";
    }
}
