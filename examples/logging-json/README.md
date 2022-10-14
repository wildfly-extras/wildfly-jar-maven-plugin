# Logging configuration WildFly bootable jar with JSON logging example

This example shows how to add JSON formatted log messages in the logging subsystem. A custom `logging.properties`
file is used for boot logging. The file is overridden during the build of the bootable JAR with the 
`<boot-logging-config>` configuration parameter.

---
**NOTE**
This is not a common use-case.
---

## Build and run

* To build: `mvn package`
(During build, the script logging-json-handler.cli is applied). This is required to synchronize our `logging.properties`
configuration with the servers logging configuration.
* To run: `mvn wildfly-jar:run`
(Notice xnio,logging and bootablejar traces displayed in the console).
* Access the application: `http://127.0.0.1:8080/`
* Notice the logs from a `org.jboss.logmanager.handlers.FileAppender` being displayed.