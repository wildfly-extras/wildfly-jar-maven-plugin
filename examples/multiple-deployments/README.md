# WildFly multi-module Jakarta EE application example

This example shows how to package multiple war files inside WildFly.

In this example the WildFly server is not packaged inside a jar file but is installed in the <project>/target/server directory.

Build and run
========

* To build and run: `mvn package wildfly-jar:run`

If you first want to package the application and run it later, you first need to install on your local maven repository the maven ear module:

* To build: `mvn install`
* To run: `mvn wildfly-jar:run`

You can verify the 2 deployments by using curl:

`curl http://127.0.0.1:8080/war1` and `curl http://127.0.0.1:8080/war2`


Build and run on OpenShift
================

* `mvn package -Popenshift`
* mkdir os && cp -r server/target/server os/
* Import the WildFly S2I builder image to run the Java application, create the image stream and deployment:
```
oc import-image wildfly-s2i --from=quay.io/jfdenise/wildfly-s2i-v2:latest --confirm

oc new-build --strategy source --binary --image-stream wildfly-s2i --name multiple-deployments

oc start-build multiple-deployments --from-dir ./os/
```

The build could take some time to end, so verify its status before creating the application, for example, checks the logs of the build pod.

```
oc new-app --name multiple-deployments-app  multiple-deployments

oc expose svc/multiple-deployments-app
```
* You can verify the application is working by using:
`curl http://$(oc get route multiple-deployments-app --template='{{ .spec.host }}')/war1`
`curl http://$(oc get route multiple-deployments-app --template='{{ .spec.host }}')/war2`

