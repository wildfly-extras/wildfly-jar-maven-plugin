package org.wildfly.plugins.demo.ejb;

public class TestEJB {
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
