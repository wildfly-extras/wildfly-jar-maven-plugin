## WildFly bootable JAR Maven plugin

This project defines a Maven plugin to build WildFly bootable JAR (starting version 20.0.0.Final). 
A WildFly bootable JAR contains both the server and your packaged application (a JAR, an EAR or a WAR).
Once the application has been built and packaged as a bootable JAR, you can start the application using the following command:

```
java -jar target/myapp-bootable.jar
```

To get the list of the startup arguments:

```
java -jar target/myapp-bootable.jar --help
```

A WildFly bootable JAR behave in a way that is similar to a WildFly server installed on file system:

* It supports the main standalone server startup arguments. 
* It can be administered/monitored using WildFly CLI.

Some limitations exist:

* The server can't be re-started automatically during a shutdown. The bootable JAR process will exit without restarting.
* Management model changes (eg: using WildFly CLI) are not persisted. Once the server is killed, management updates are lost.
* Server can't be started in admin mode.

NB: When started, the bootable JAR installs a WildFly server in the _TEMP_ directory. The bootable JAR displayed traces contain the actual path to this transient installation. This 
installation is deleted when the bootable JAR process exits.

## Examples

The [examples](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples) directory 
contains Maven example projects that highlight various usages of the WildFly bootable JAR. Build and run these projects
to familiarize yourself with the Maven plugin. A good example to start with is the 
[jaxrs](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/jaxrs) example.

Some examples are targeting OpenShift deployment, for example:

* The [jkube](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/jkube) example shows how to make use of the [Eclipse JKube](https://www.eclipse.org/jkube/) Maven plugin.

* The [microprofile-config](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/microprofile-config) and 
[postgresql](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/postgresql) examples show how to use the OpenShift 'oc' command to deploy a bootable JAR on OpenShift.

* [jib](https://github.com/wildfly-extras/wildfly-jar-maven-plugin/tree/master/examples/jib) example shows how to deploy a bootable JAR inside a [JIB](https://github.com/GoogleContainerTools/jib) container.

## Building the project

The master branch depends on latest WildFly that you need to build locally.

* git clone https://github.com/wildfly/wildfly
* mvn clean install -DskipTests
* git clone https://github.com/wildfly-extras/wildfly-jar-maven-plugin
* mvn clean install -DskipTests
