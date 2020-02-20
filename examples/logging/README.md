# JAXRS + logging configuration WildFly bootable jar example

* To build: mvn package
(During build, the script logging.cli is applied).

* To run: mvn wildfly-bootable-jar:run
(Notice xnio,logging and bootablejar traces displayed in the console).

* Access the application: http://127.0.0.1:8080/hello
* Notice the com.example.demo.rest.HelloWorldEndpoint debug trace.
