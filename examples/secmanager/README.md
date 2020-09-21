# Security Manager WildFly bootable jar example

An example of how to use security manager permissions.

Build and run
=============

During packaging the permission to read properties is granted to deployments.

* To build: `mvn package`
* To run: `java -jar target/sec-manager-bootable.jar -secmgr`
* Access the application: 
    * http://127.0.0.1:8080/hello/read  - Should succeed as the read action is allowed.
    * http://127.0.0.1:8080/hello/write - Should fail with an `AccessControlException` as the write permission was not granted. 
