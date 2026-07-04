package com.aiot.interfaces.amqp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record IotdaAmqpEnvelope(
        @JsonProperty("resource") String resource,
        @JsonProperty("event") String event,
        @JsonProperty("event_time") String eventTime,
        /** 时延信息，HTTP 推送时启用，AMQP 推送时为空 */
        @JsonProperty("event_time_ms") String eventTimeMs,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("notify_data") IotdaNotifyData notifyData
) {}

record IotdaNotifyData(
        @JsonProperty("header") IotdaHeader header,
        @JsonProperty("body") IotdaBody body
) {}

record IotdaHeader(
        @JsonProperty("app_id") String appId,
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("product_id") String productId,
        @JsonProperty("gateway_id") String gatewayId
) {}

record IotdaBody(
        @JsonProperty("services") List<IotdaServiceData> services
) {}

record IotdaServiceData(
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("event_time") String eventTime,
        @JsonProperty("properties") Map<String, Object> properties
) {}
