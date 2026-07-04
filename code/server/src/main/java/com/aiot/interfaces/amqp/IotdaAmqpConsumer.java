package com.aiot.interfaces.amqp;

import jakarta.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Hashtable;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IotdaAmqpConsumer {

    private static final Logger log = LoggerFactory.getLogger(IotdaAmqpConsumer.class);

    private final IotdaAmqpProperties properties;
    private final IotdaAmqpMessageRouter router;
    private Connection connection;
    private Session session;
    private final Hashtable<String, BiConsumer<String, String>> topicHandlers = new Hashtable<>();

    public IotdaAmqpConsumer(IotdaAmqpProperties properties, IotdaAmqpMessageRouter router) {
        this.properties = properties;
        this.router = router;
    }

    public void start() {
        String host = properties.getHost();
        if (host == null || host.isEmpty()) {
            log.info("AMQP 未配置 (aiot.amqp.host 为空)，跳过启动");
            return;
        }

        try {
            String url = properties.buildUrl();
            log.info("AMQP 连接中: {}", properties.getHost());

            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
            env.put("connectionfactory.amqpFactory", url);
            Context ctx = new InitialContext(env);
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("amqpFactory");

            connection = cf.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            String queue = properties.getQueueName();
            Topic topic = session.createTopic(queue);
            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(this::onMessage);

            log.info("AMQP 已连接，订阅队列: {}", queue);

        } catch (Exception e) {
            log.error("AMQP 连接失败: {}", e.getMessage());
        }
    }

    public void stop() {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
            log.info("AMQP 已断开");
        } catch (Exception e) {
            log.warn("AMQP 关闭异常: {}", e.getMessage());
        }
    }

    public void registerHandler(String topic, BiConsumer<String, String> handler) {
        topicHandlers.put(topic, handler);
    }

    private void onMessage(Message msg) {
        try {
            String body;
            if (msg instanceof TextMessage tm) {
                body = tm.getText();
            } else if (msg instanceof BytesMessage bm) {
                byte[] data = new byte[(int) bm.getBodyLength()];
                bm.readBytes(data);
                body = new String(data);
            } else {
                body = msg.getBody(Object.class).toString();
            }

            String topic = msg.getJMSType();
            if (topic == null) topic = properties.getQueueName();

            BiConsumer<String, String> handler = topicHandlers.get(topic);
            if (handler != null) {
                handler.accept(topic, body);
            } else {
                router.route(body);
            }
        } catch (Exception e) {
            log.error("AMQP 消息处理异常", e);
        }
    }
}
