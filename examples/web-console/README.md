# Web Console WildFly bootable jar example

Provision a server with a JAX-RS resource and activates the HAL Web Console. A CLI script adds the user admin/admin in order to have access to the web console.

Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`
* Access the Web Console: `http://127.0.0.1:9990` (user admin, password admin)
* Access the application: `http://127.0.0.1:8080/hello`


## Notes
* Remember the Bootable Jar is started in read-only mode and any change applied by using the Web Console will not be persisted if your application is stopped and launched again.
* Add the `request-controller` Galleon layer if you need to use the [Suspend, Resume and Graceful shutdown](https://docs.wildfly.org/20/Admin_Guide.html#Suspend) server capabilities.
* The server cannot be re-started automatically during a shutdown. The bootable jar process will exit without restarting.
