package org.wildfly.plugins.demo.ejb;

import javax.ejb.Stateless;

@Stateless(name = "FOO")
public class GreeterEJB {
    /**
     * This method takes a name and returns a personalised greeting.
     *
     * @param name
     *            the name of the person to be greeted
     * @return the personalised greeting.
     */
    public String sayHello(String name) {
        return "Hello " + name;
    }
}
