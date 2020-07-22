# Security Manager WildFly bootable jar example

During packaging the permission to read properties is granted to deployments.

* To build: mvn package
* Update the file ./mypolicy codebase URL with absolute path to  target/sec-manager-bootable.jar
* To run: java -Djava.security.manager -Djava.security.policy=mypolicy -jar target/sec-manager-bootable.jar
* Access the application: http://127.0.0.1:8080/hello
