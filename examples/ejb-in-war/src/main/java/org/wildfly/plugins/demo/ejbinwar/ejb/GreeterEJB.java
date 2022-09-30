package org.wildfly.plugins.demo.ejbinwar.ejb;

import jakarta.ejb.Stateful;

/**
 * A simple Hello World EJB. The EJB does not use an interface.
 *
 * @author paul.robinson@redhat.com, 2011-12-21
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
