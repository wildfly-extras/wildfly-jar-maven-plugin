package fhw;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.TextMessage;

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
