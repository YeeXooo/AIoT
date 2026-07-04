package com.aiot.interfaces.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class IotdaAmqpBootstrap {

    private static final Logger log = LoggerFactory.getLogger(IotdaAmqpBootstrap.class);

    private final IotdaAmqpConsumer consumer;

    public IotdaAmqpBootstrap(IotdaAmqpConsumer consumer) {
        this.consumer = consumer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("启动 AMQP 消费者");
        consumer.start();
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        consumer.stop();
    }
}
