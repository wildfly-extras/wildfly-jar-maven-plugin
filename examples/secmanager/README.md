# Security Manager WildFly bootable jar example

During packaging the permission to read properties is granted to deployments.

* To build: mvn package
* To run: java -jar target/sec-manager-bootable.jar -secmgr
* Access the application: http://127.0.0.1:8080/hello
