package fhw;

import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

@MessageDriven(
    activationConfig =
    {
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "simpleMDBTestQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue")

    })
public class SimpleMDB
    implements MessageListener
{

    @Override
    public void onMessage(Message msg)
    {
        TextMessage textMessage = (TextMessage) msg;
        try
        {
            System.out.println("Message received: " + textMessage.getText());
        }
        catch (JMSException e)
        {
            System.out.println("Error while trying to consume messages: " + e.getMessage());
        }
    }
}
