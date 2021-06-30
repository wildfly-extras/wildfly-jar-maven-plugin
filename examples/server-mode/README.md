# JAX-RS unpackaged WildFly example

Build a server containing a JAX-RS resource.

Build and run
========

* To build and run arquilian tests: `mvn clean verify`
* To run: `mvn wildfly-jar:run`
* Access the application: `http://127.0.0.1:8080/hello`

JKube build and deployment for OpenShift
========================================

WARNING, depends on SNAPSHOT Build of JKube based on branch
https://github.com/jfdenise/jkube/tree/wildfly-s2i-v2-new-generator

Make sure you are logged in to your OpenShift Cluster

The following command will create and deploy the Server on OpenShift:

`mvn oc:deploy -Popenshift`

