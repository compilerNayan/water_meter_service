package com.vswitch.watermeter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * EventBridge scheduled entry point for mock telemetry ingestion.
 */
public class TelemetryIngestionLambdaHandler implements RequestHandler<ScheduledEvent, String> {

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
        MockTelemetrySchedulerService scheduler =
                getApplicationContext().getBean(MockTelemetrySchedulerService.class);
        scheduler.runScheduledIngestion();
        return "OK";
    }
}
