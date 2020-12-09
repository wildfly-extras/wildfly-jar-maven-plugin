# Logging configuration WildFly bootable jar example

An example showing how to change the Bootable Jar server logging configuration. 

Build and run
=============

* To build: `mvn package`
(During build, the script logging.cli is applied).
* To run: `mvn wildfly-jar:run`.
* Note you should see the logs being output in JSON format.
* Access the application: `http://127.0.0.1:8080/hello`
* Notice the `org.wildfly.plugins.demo.logging.HelloWorldEndpoint` debug trace in the console.
