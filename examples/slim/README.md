# WildFly slim bootable jar and local maven repository generation Example

This is an example of using `jboss-maven-dist` plugin option to store all the JBoss Modules maven artifacts used by the Bootable Jar in an external maven repository, reducing the size of the generated JAR file.

When you are building this example, all the JBoss Modules artifacts will be stored on the `my-maven-repo` directory. You can make a reference to them by specifying the maven repository to use.

Build and run
=============

* To build: `mvn package`
* To run: `java -Dmaven.repo.local=<absolute path to wildfly jar maven project>/examples/slim/target/my-maven-repo -jar target/slim-bootable.jar`
* Access the application: `http://127.0.0.1:8080/hello`

JKube build and deployment for OpenShift
========================================

JKube automatically detects the generated maven local cache and installs it in the generated Docker image. In addition, the system property `-Dmaven.repo.local`
is added when launching the server.

The following command will create and deploy the Bootable JAR on OpenShift:

`mvn oc:deploy -Popenshift`

Make sure you are logged in to your OpenShift Cluster before you try build/deploy