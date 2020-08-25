# WildFly bootable hollow jar example

Build a bootable JAR containing no deployment for both bare-metal and OpenShift.

* To build: mvn package
* To run: mvn wildfly-jar:run

To build the bootable JAR for OpenShift:

* mvn package -Popenshift

When building a bootable JAR for OpenShift, a set of WildFly CLI commands are applied to the server configuration in order to
adjust it to the OpenShift context. The applied script can be retrieved in target/bootable-jar-build-artifacts/generated-cli-script.txt file.

You can check community documentation to retrieve information on the cloud specific changes.