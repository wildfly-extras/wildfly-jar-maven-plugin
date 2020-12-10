# Development mode (watch of your source files)

A mode that allows you to build the bootable jar once and to start it. The changes made to your source files 
are detected and your application is re-built/re-deployed. Changes to the pom.xml file (update layers, feature-packs, ...) makes the server to be rebuilt.

* cd jaxrs
* mvn wildfly-jar:dev-watch (this goal builds your application, build an hollow bootable JAR and start it)
* Do changes in your sources, your application is rebuilt and redeployed.
* When done: Ctrl-C in the console to kill the running goal and bootable JAR.


# Development mode (with re-packaging)

A mode that allows you to build the bootable jar once and to start it. Make changes to your
code then rebuild. Newly packaged application is reloaded by the running server.

* cd jaxrs
* mvn wildfly-jar:dev (build hollow server and start it)
* mvn package -Ddev (notice the deployment scanner that discover the deployed application)
* Do changes in code.
* mvn package -Ddev (notice the deployment scanner that discover the refreshed deployed application)
* When done: mvn wildfly-jar:shutdown