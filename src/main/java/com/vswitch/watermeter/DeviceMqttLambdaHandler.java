package com.vswitch.watermeter;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.vswitch.watermeter.device.IotMqttIngestionService;

/** AWS IoT Core rule entry point for device MQTT telemetry and lifecycle events. */
public class DeviceMqttLambdaHandler implements RequestHandler<Map<String, Object>, String> {

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
    public String handleRequest(Map<String, Object> event, Context lambdaContext) {
        IotMqttIngestionService ingestionService =
                getApplicationContext().getBean(IotMqttIngestionService.class);
        ingestionService.handleEvent(event);
        return "OK";
    }
}
