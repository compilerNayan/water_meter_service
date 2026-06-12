package com.vswitch.watermeter.device;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DeviceMqttClient implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(DeviceMqttClient.class);
    private static final int QOS = 1;

    private final DeviceMqttResponseTracker responseTracker;
    private final ObjectMapper objectMapper;
    private final Object connectionLock = new Object();

    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();
    private MqttClient client;

    DeviceMqttClient(DeviceMqttResponseTracker responseTracker, ObjectMapper objectMapper) {
        this.responseTracker = responseTracker;
        this.objectMapper = objectMapper;
    }

    void publish(String topic, String payload) {
        ensureConnected();
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            message.setQos(QOS);
            client.publish(topic, message);
            log.debug("Published MQTT message to {}", topic);
        } catch (MqttException e) {
            throw new IllegalStateException("Failed to publish MQTT message to " + topic, e);
        }
    }

    void ensureSubscribed(String topic) {
        ensureConnected();
        if (!subscribedTopics.add(topic)) {
            return;
        }
        try {
            client.subscribe(topic, QOS);
            log.info("Subscribed to MQTT topic {}", topic);
        } catch (MqttException e) {
            subscribedTopics.remove(topic);
            throw new IllegalStateException("Failed to subscribe to MQTT topic " + topic, e);
        }
    }

    private void ensureConnected() {
        synchronized (connectionLock) {
            if (client != null && client.isConnected()) {
                return;
            }
            connect();
        }
    }

    private void connect() {
        try {
            if (client != null) {
                try {
                    client.disconnectForcibly();
                } catch (Exception ignored) {
                    // reconnecting
                }
                try {
                    client.close();
                } catch (Exception ignored) {
                    // reconnecting
                }
            }

            String brokerHost = brokerHost();
            String clientId = "water-meter-backend-" + UUID.randomUUID();
            client = new MqttClient("ssl://" + brokerHost + ":8883", clientId, new MemoryPersistence());
            client.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setSocketFactory(sslSocketFactory());
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);

            client.connect(options);
            subscribedTopics.clear();
            log.info("Connected to AWS IoT MQTT broker {}", brokerHost);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to AWS IoT MQTT broker", e);
        }
    }

    private static String brokerHost() {
        String endpoint = IotMqttCredentials.MQTT_ENDPOINT;
        String withoutScheme =
                endpoint.startsWith("mqtts://")
                        ? endpoint.substring("mqtts://".length())
                        : endpoint.startsWith("ssl://")
                                ? endpoint.substring("ssl://".length())
                                : endpoint;
        int slash = withoutScheme.indexOf('/');
        return slash >= 0 ? withoutScheme.substring(0, slash) : withoutScheme;
    }

    private static SSLSocketFactory sslSocketFactory() throws Exception {
        return IotMqttSslContexts.fromPem(
                        IotMqttCredentials.CA_CERTIFICATE_PEM,
                        IotMqttCredentials.CLIENT_CERTIFICATE_PEM,
                        IotMqttCredentials.CLIENT_PRIVATE_KEY_PEM)
                .getSocketFactory();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost", cause);
        subscribedTopics.clear();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        MqttTopicParser.parse(topic)
                .filter(parsed -> DeviceMqttTopics.SUFFIX_STATUS.equals(parsed.suffix()))
                .ifPresent(
                        parsed -> {
                            String payload =
                                    new String(
                                            message.getPayload(),
                                            java.nio.charset.StandardCharsets.UTF_8);
                            log.debug("MQTT status message on {}: {}", topic, payload);
                            var responseBody =
                                    DeviceMqttHttpPayloadParser.parseJsonBody(
                                            java.util.Map.of("payload", payload), objectMapper);
                            responseTracker.completeResponse(
                                    parsed.tenantId(), parsed.deviceId(), responseBody);
                        });
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op
    }
}
