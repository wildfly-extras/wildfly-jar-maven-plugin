# JAXRS WildFly bootable jar jib generated image

We are using the packaged jar support of jib.
In this apporoach, the bootable jar is rebuilt each time the app is built, sub-optimal.
For a decoupling of server packaging and app packaging check ../jib-layers example.

* To build: mvn package
* To run: docker run wildfly/jaxrs-jib
* Inspect the running container for IP address
* Access the application: http://<container ip>:8080/hello

Deploy/Run in openshift
=======================
* Push the image to a docker repository (can update pom.xml to do tha tdirectory in jib plugin config).
* oc new-app <image>
* Create a service (selector deploymentconfig: jaxrs-jib, port 8080)
* Expose the service
* Access the application: http://<route>/hello
