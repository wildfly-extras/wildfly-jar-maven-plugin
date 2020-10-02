# WildFly JSF with EJBs and persistence bootable JAR example

This example shows how to combine JSF EJB and JPA. The example is a simple CRUD application that access to an in memory H2 data base. The H2 datasource is created by a CLI script executed by the wildfly-jar plugin at build time.

Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`
* Access the application on a web browser: `http://127.0.0.1:8080/`


Build and run on OpenShift
==========================

* mvn package -Popenshift
* mkdir os && cp target/jsf-ejb-jpa-bootable.jar os/
* Import the OpenJDK 11 image to run the Java application, create the image stream and deployment:
```
oc import-image ubi8/openjdk-11 --from=registry.redhat.io/ubi8/openjdk-11 --confirm

oc new-build --strategy source --binary --image-stream openjdk-11 --name jsf-ejb-jpa-bootable-jar

oc start-build jsf-ejb-jpa-bootable-jar --from-dir ./os/

oc new-app --name jsf-ejb-jpa-bootable-jar-app \
    --env GC_MAX_METASPACE_SIZE=256 \
    --env GC_METASPACE_SIZE=96 \
    jsf-ejb-jpa-bootable-jar

oc expose svc/jsf-ejb-jpa-bootable-jar-app
```
* You can verify the application is working by using:
`curl http://$(oc get route jsf-ejb-jpa-bootable-jar-app --template='{{ .spec.host }}')`

