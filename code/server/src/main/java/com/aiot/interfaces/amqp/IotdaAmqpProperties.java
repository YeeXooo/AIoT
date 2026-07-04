package com.aiot.interfaces.amqp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "aiot.amqp")
public class IotdaAmqpProperties {

    private String host = "";
    private int port = 5671;
    private String accessKey = "";
    private String accessCode = "";
    private String queueName = "default";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getAccessCode() { return accessCode; }
    public void setAccessCode(String accessCode) { this.accessCode = accessCode; }

    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }

    public String buildUrl() {
        String username = "accessKey=" + accessKey + "|timestamp=" + System.currentTimeMillis();
        username = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String password = URLEncoder.encode(accessCode, StandardCharsets.UTF_8);
        return String.format("amqps://%s:%d?amqp.saslMechanisms=PLAIN&jms.username=%s&jms.password=%s",
                host, port, username, password);
    }
}
