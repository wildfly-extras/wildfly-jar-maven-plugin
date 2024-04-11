# JAX-RS WildFly bootable jar Jib generated image

We are using the packaged JAR and WAR support of Java Image Builder Jib.
This example separates the creation of a hollow bootable JAR Jib image from the application JIB image. This allows for efficient application image build. The bootable JAR is built only once.

WARNING: due to https://github.com/GoogleContainerTools/jib/issues/4134 you must use Docker.

* To build the hollow JAR JIB image

  * cd server-layer
  * mvn package
(The generated image wildfly/jaxrs-server-jib is the FROM image of the application JIB image built in next step).
 
* To build the application JIB image

  * cd app-layer
  * mvn package

* To run: docker run -p 8080:8080 wildfly/jaxrs-layers-jib

* Access the application: http://localhost:8080/hello
