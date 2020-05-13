# Development mode

A mode that allows you to build the bootable jar once and to start it. Make changes to your
code then rebuild. Newly packaged application is reloaded by the running server.

* cd jaxrs
* mvn wildfly-jar:dev (build hollow server and start it)
* mvn package -Ddev (notice the deployment scanner that discover the deployed application)
* Do changes in code.
* mvn package -Ddev (notice the deployment scanner that discover the refreshed deployed application)
* When done: mvn wildfly-jar:shutdown