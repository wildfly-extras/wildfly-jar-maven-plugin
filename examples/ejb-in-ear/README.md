# WildFly multi-module Jakarta EE application example

This example shows how to use an enterprise archive (EAR) with the WildFly Bootable JAR.

The example is an EAR that contains an EJB module and a WAR module. It deploys a JAX-RS resource that access to an EJB.

Build and run
=============

The EAR is created by using a parent child maven module. The WildFly Jar maven plugin creates the Bootable Jar file by using the ear maven module. That means, if you want to run the example without installing any artifact on your local maven repository, you need to package and run it in a single maven command:

* To build and run: `mvn package wildfly-jar:run`

If you first want to package the application and run it later, you first need to install on your local maven repository the maven ear module:

* To build: `mvn install`
* To run: `mvn wildfly-jar:run`

You can verify the application by using curl:

`curl http://127.0.0.1:8080/hello/bootable


Build and run on OpenShift
==========================

* `mvn package -Popenshift`
* mkdir os && cp ear/target/ejb-in-ear-bootable.jar os/
* Import the OpenJDK 17 image to run the Java application, create the image stream and deployment:
```
oc import-image ubi8/openjdk-17 --from=registry.redhat.io/ubi8/openjdk-17 --confirm

oc new-build --strategy source --binary --image-stream openjdk-17 --name ejb-in-ear-bootable-jar

oc start-build ejb-in-ear-bootable-jar --from-dir ./os/
```

The build could take some time to end, so verify its status before creating the application, for example, checks the logs of the build pod.

```
oc new-app --name ejb-in-ear-bootable-jar-app ejb-in-ear-bootable-jar

oc expose svc/ejb-in-ear-bootable-jar-app
```
* You can verify the application is working by using:
`curl http://$(oc get route ejb-in-ear-bootable-jar-app --template='{{ .spec.host }}')/hello/bootable`

