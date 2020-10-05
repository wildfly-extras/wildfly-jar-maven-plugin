# Logging configuration WildFly bootable jar with log4j appender example

This example shows how to use a log4j appender as a custom-handler in the logging subsystem. A custom `logging.properties`
file is used for boot logging. The file is overridden during the build of the bootable JAR with the 
`<boot-logging-config>` configuration parameter.

---
**NOTE**
This is not a common use-case.
---

## Build and run

* To build: `mvn package`
(During build, the script logging-log4j-appender.cli is applied). This is required to synchronize our `logging.properties`
configuration with the servers logging configuration.
* To run: `mvn wildfly-jar:run`
(Notice xnio,logging and bootablejar traces displayed in the console).
* Access the application: `http://127.0.0.1:8080/`
* Notice the logs from a `org.apache.log4j.FileAppender` being displayed.

## Details

This uses some internal API's that are not meant to generally be used outside of the container. However, if you want to
use a log4j appender as a `custom-handler` these steps would be required.

The handler to use is specific to the container. The type is `org.jboss.as.logging.logmanager.Log4jAppenderHandler`
which is located in the `org.jboss.as.logging` module. The properties in the example are important and must be present.

The `postConfiguration` property should generally be present and must be set to `activate`. This is required for any
`org.apache.log4j.spi.OptionHandler`. This is also where the `dummy` property is required. The value is empty, but can
really be any value. It's used to ensure the triggering from the activation.

The `appender` property is the name of the POJO, described below, which configures the actual appender.

```
handler.log4j-file=org.jboss.as.logging.logmanager.Log4jAppenderHandler
handler.log4j-file.module=org.jboss.as.logging
handler.log4j-file.level=ALL
handler.log4j-file.formatter=json
handler.log4j-file.postConfiguration=activate
handler.log4j-file.properties=appender,enabled,dummy
handler.log4j-file.appender=log4j-file
handler.log4j-file.enabled=true
handler.log4j-file.dummy=
```

Next is the POJO section. This is how the appender itself is configured. The POJO is defined as the 
`handler.$NAME.appender` property on the handler configuration.

```
# POJOs to configure
pojos=log4j-file

pojo.log4j-file=org.apache.log4j.FileAppender
pojo.log4j-file.module=org.apache.log4j
pojo.log4j-file.properties=name,file,append,immediateFlush
pojo.log4j-file.name=log4j-file
pojo.log4j-file.file=${jboss.server.log.dir}/log4j.log
pojo.log4j-file.append=true
pojo.log4j-file.immediateFlush=true
```