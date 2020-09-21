## WildFly bootable jar maven plugin

This project defines a maven plugin to build bootable jars for WildFly (starting version 20.0.0.Final). 
A WildFly bootable jar contains both the server and your packaged application (a jar, a ear or a war).
Once the application has been built and packaged as a bootable jar, you can start the application using the following command:

```
java -jar target/myapp-bootable.jar
```

To get the list of the startup arguments:

```
java -jar target/myapp-bootable.jar --help
```

A WildFly bootable jar behave in a way that is similar to a WildFly server installed on file system:

* It supports the main standalone server startup arguments. 
* It can be administered/monitored using JBoss CLI.

Some limitations exist:

* The server can't be re-started automatically during a shutdown. The bootable jar process will exit without restarting.
* Management model changes (eg: using JBoss CLI) are not persisted. Once the server is killed, management updates are lost.
* Server can't be started in admin mode.

NB: When started, the bootable jar installs a WildFly server in the _TEMP_ directory. The bootable jar displayed traces contain the actual path to this transient installation. This 
installation is deleted when the bootable jar process exits.

## Examples

The directory [examples](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples)
contains maven example projects that highlight various usages of the WildFly bootable jar. Build and run these projects
to familiarize yourself with the maven plugin. A good example to start with is the 
[jaxrs](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/jaxrs) example.

Some of these examples are targeting deployment of the bootable jar in OpenShift. 
For example: [microprofile-config](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/microprofile-config) and 
[postgresql](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/postgresql).

Deployment inside a [JIB](https://github.com/GoogleContainerTools/jib) container is 
covered by [jib](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/jib) example and _examples/jib-*_ projects.

