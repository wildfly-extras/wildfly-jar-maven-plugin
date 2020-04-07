# JAXRS + logging configuration WildFly bootable jar example

* To build: mvn package
(During build, the script logging.cli is applied).

* To run: mvn wildfly-jar:run
(Notice xnio,logging and bootablejar traces displayed in the console).
* Note the server installation dir in the log: "WFLYJAR0007: Installed server and application in <server installation dir>"
* Access the application: http://127.0.0.1:8080/hello
* Notice the com.example.demo.rest.HelloWorldEndpoint debug trace in the console.
* The file <server installation dir>/standalone/logs/json.log contains the application trace in json format.
