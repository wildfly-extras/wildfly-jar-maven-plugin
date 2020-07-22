# WildFly slim bootable jar and local maven repository generation Example

* To build: mvn package
* To run: java -Dmaven.repo.local=\<absolute path to wildfly jar maven project\>/examples/slim/my-maven-repo -jar target/slim-bootable.jar
* Access the application: http://127.0.0.1:8080/hello
