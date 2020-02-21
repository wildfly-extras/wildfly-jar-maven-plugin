# JAXRS WildFly bootable jar jib generated image

We are using the packaged jar and WAR support of jib.
This example separates the creation of an hollow bootable jar JIB image
from the application JIB image. This allows for efficient application image build. The bootable jar is built only once.

NB: you need to update server-layer/pom.xml and app-layer/pom.xml files with your docker registry to push base image to.

* To build the hollow jar JIB image
** cd server-layer
** mvn package
(The generated image is the FROM image of the application JIB image built in next step).
 
* To build the application JIB image 
** cd app-layer
** mvn package

* To run: docker run wildfly/jaxrs-jib

* Inspect the running container for IP address.

* Access the application: http://<container ip>:8080/hello
