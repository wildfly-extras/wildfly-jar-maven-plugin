# JAX-RS WildFly bootable jar jib generated image

We are using the packaged jar support of jib.

The directory /opt/wildfly is created in order for the WildFly operator to properly manage this image.

Build and run
=============

* To build: `mvn package`
* To run: `docker run -p 8080:8080 wildfly/jaxrs-operator-jib`
* Access the application: `http://localhost:8080/hello`

Deploy and run in OpenShift
=======================

* Push the image to a docker repository (can update pom.xml to do that directly in jib plugin config).
* oc new-app <image>
* Create a service (selector deploymentconfig: jaxrs-operator-jib, port 8080)
* Expose the service
* Access the application: http://\<route\>/hello
