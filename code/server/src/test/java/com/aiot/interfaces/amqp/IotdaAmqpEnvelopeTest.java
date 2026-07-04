package com.aiot.interfaces.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IotdaAmqpEnvelopeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SAMPLE_JSON = """
            {
              "resource": "device.property",
              "event": "report",
              "event_time": "20260703T183519Z",
              "event_time_ms": "2026-07-03T18:35:19.480Z",
              "request_id": "65fb87b2-70dd-4f34-adae-c5e668fd9b2e",
              "notify_data": {
                "header": {
                  "app_id": "b95d5f94678e4505927f7bbe13d345cf",
                  "device_id": "6a44f1047f2e6c302f80df85_vehicle_safety",
                  "node_id": "vehicle_safety",
                  "product_id": "6a44f1047f2e6c302f80df85",
                  "gateway_id": "6a44f1047f2e6c302f80df85_vehicle_safety"
                },
                "body": {
                  "services": [{
                    "service_id": "VehicleSafety",
                    "event_time": "20260703T183519Z",
                    "properties": {
                      "temp": 24.0,
                      "humi": 60.0,
                      "lux": 65,
                      "lat": 41.8028,
                      "lon": 123.5497,
                      "gps_fix": 1,
                      "risk": 2,
                      "perclos": 0.35,
                      "yawn": 6,
                      "hard_brake": 25,
                      "hard_accel": 59,
                      "sharp_turn": 32,
                      "hr": 72,
                      "spo2": 98.5,
                      "resting_hr": 65,
                      "battery_mv": 3200,
                      "pc_lvl": 2,
                      "score": 85,
                      "ax": 0.2,
                      "ay": 0.0,
                      "az": 9.8,
                      "phone": 1,
                      "radar_human": 0
                    }
                  }]
                }
              }
            }""";

    @Test
    void shouldDeserializeEnvelope() throws Exception {
        IotdaAmqpEnvelope envelope = MAPPER.readValue(SAMPLE_JSON, IotdaAmqpEnvelope.class);

        assertEquals("device.property", envelope.resource());
        assertEquals("report", envelope.event());
        assertEquals("20260703T183519Z", envelope.eventTime());
        assertEquals("2026-07-03T18:35:19.480Z", envelope.eventTimeMs());
    }

    @Test
    void shouldDeserializeHeader() {
        IotdaAmqpEnvelope envelope = assertDoesNotThrow(() ->
                MAPPER.readValue(SAMPLE_JSON, IotdaAmqpEnvelope.class));

        IotdaHeader header = envelope.notifyData().header();
        assertEquals("b95d5f94678e4505927f7bbe13d345cf", header.appId());
        assertEquals("6a44f1047f2e6c302f80df85_vehicle_safety", header.deviceId());
        assertEquals("vehicle_safety", header.nodeId());
        assertEquals("6a44f1047f2e6c302f80df85", header.productId());
    }

    @Test
    void shouldDeserializeServiceData() {
        IotdaAmqpEnvelope envelope = assertDoesNotThrow(() ->
                MAPPER.readValue(SAMPLE_JSON, IotdaAmqpEnvelope.class));

        assertEquals(1, envelope.notifyData().body().services().size());

        IotdaServiceData svc = envelope.notifyData().body().services().get(0);
        assertEquals("VehicleSafety", svc.serviceId());
        assertEquals("20260703T183519Z", svc.eventTime());
        assertEquals(23, svc.properties().size());
    }

    @Test
    void shouldDeserializePropertiesWithCorrectTypes() {
        IotdaAmqpEnvelope envelope = assertDoesNotThrow(() ->
                MAPPER.readValue(SAMPLE_JSON, IotdaAmqpEnvelope.class));

        var props = envelope.notifyData().body().services().get(0).properties();
        assertEquals(24.0, props.get("temp"));
        assertEquals(60.0, props.get("humi"));
        assertEquals(65, props.get("lux"));
        assertEquals(41.8028, props.get("lat"));
        assertEquals(2, props.get("risk"));
        assertEquals(0.35, props.get("perclos"));
        assertEquals(6, props.get("yawn"));
        assertEquals(72, props.get("hr"));
        assertEquals(98.5, props.get("spo2"));
        assertEquals(3200, props.get("battery_mv"));
        assertEquals(85, props.get("score"));
    }
}
