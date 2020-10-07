# JAX-RS / Arquillian testing example

Build a bootable JAR containing a JAX-RS resource and test it using Arquillian.

In this scenario, the Arquillian test is run as a client (test annotated with ```@RunAsClient```).

The Arquillian XML descriptor is located in _src/test/resources/arquillian.xml_

Build, test and run
===================

* To build and run tests: `mvn clean verify`
* To run: `mvn wildfly-jar:run`
* Access the application: `http://127.0.0.1:8080/hello`
