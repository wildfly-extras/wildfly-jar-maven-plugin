# WildFly EJB in WAR bootable JAR Example

This example shows how you can use EJBs packaged in a WAR file with the Bootable JAR. The example is a simple application using EJBs and JSF.

Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`
* Access the application: `http://127.0.0.1:8080/`


Build and run on OpenShift
==========================

* mvn package -Popenshift
* mkdir os && cp target/ejb-in-war-bootable.jar os/
* Import the OpenJDK 11 image to run the Java application, create the image stream and deployment:
```
oc import-image ubi8/openjdk-11 --from=registry.redhat.io/ubi8/openjdk-11 --confirm

oc new-build --strategy source --binary --image-stream openjdk-11 --name ejb-bootable-jar

oc start-build ejb-bootable-jar --from-dir ./os/

oc new-app --name ejb-bootable-jar-app ejb-bootable-jar

oc expose svc/ejb-bootable-jar-app
```
* You can verify the application is working by using:
`curl http://$(oc get route ejb-bootable-jar-app --template='{{ .spec.host }}')`

 
