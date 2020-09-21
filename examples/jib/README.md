# JAX-RS WildFly bootable jar jib generated image

We are using the packaged jar support of Java Image Builder Jib.
In this approach, the bootable jar is rebuilt each time the app is built, sub-optimal.
For a decoupling of server packaging and app packaging check ../jib-layers example.

Build and run
=============

* To build: `mvn package`
* To run: `docker run -p 8080:8080 wildfly/jaxrs-jib`
* Access the application: `http://localhost:8080/hello`

Build and run in OpenShift
=======================
* Push the image to a docker repository (can update pom.xml to do that directory in jib plugin config).
* oc new-app <image>
* Create a service (selector deploymentconfig: jaxrs-jib, port 8080)
* Expose the service
* Access the application: http://\<route\>/hello
