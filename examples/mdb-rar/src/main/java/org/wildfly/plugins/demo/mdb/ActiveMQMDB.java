package org.wildfly.plugins.demo.mdb;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import org.jboss.ejb3.annotation.ResourceAdapter;

@MessageDriven(
        activationConfig = {
            @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
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
