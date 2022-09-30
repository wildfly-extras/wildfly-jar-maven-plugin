package org.wildfly.plugins.demo.mdb;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.TextMessage;
import org.jboss.ejb3.annotation.ResourceAdapter;

@MessageDriven(
        activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
            @ActivationConfigProperty(propertyName = "destination", propertyValue = "java:jboss/activemq/queue/LocalRPCRequest")
        },
        name = "ActiveMQMDB")
@ResourceAdapter(value = "activemq-rar.rar")
public class ActiveMQMDB implements MessageListener {

    public ActiveMQMDB() {
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage) {
                System.out.println("Got Message " + ((TextMessage) message).getText());
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
