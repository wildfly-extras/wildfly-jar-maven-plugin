package org.wildfly.plugins.demo.hollowjar;

import javax.ejb.Stateful;

/**
 * A simple Hello World EJB. The EJB does not use an interface.
 *
 */
@Stateful
public class GreeterEJB {
    /**
     * This method takes a name and returns a personalised greeting.
     *
     * @param name the name of the person to be greeted
     * @return the personalised greeting.
     */
    public String sayHello(String name) {
        return "Hello " + name;
    }
}
