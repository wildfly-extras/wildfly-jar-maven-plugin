# Hollow JAR / Arquillian testing example

Arquillian is used to deploy a JAX-RS resource and an EJB into the Hollow JAR.

In this scenario, part of the Arquillian test is run as a client 
(testcase annotated with ```@RunAsClient```) and in-container.

The Arquillian XML descriptor is located in _src/test/resources/arquillian.xml_

Build, test and run
===================

* To build and run tests: `mvn clean verify`
* To run: `mvn wildfly-jar:run`
* Access the application: `http://127.0.0.1:8080/hello`
