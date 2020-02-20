# JAXRS WildFly bootable jar jib generated image

We are using the packaged jar support of jib.
This example separates the creation of an hollow bootable jar (inside a base JIB image)
from the application image. Allowing for efficient application image build.

NB: you need to update pom.xml with your docker registry to push base image to.

* To build the hollow jar image: mvn package -Pjaxrs-server
(The generated image is the FROM image of the application image built in next step).
 
* To build the application image: mvn package -Pjaxrs-app

* To run: docker run wildfly/jaxrs-jib

* Inspect the running container for IP address.

* Access the application: http://<container ip>:8080/hello
