# Highlight development mode. 

A mode that allows you to build the bootable jar once and to start it, then make changes to your
code. Newly packaged application is reloaded by the running server.

* cd jaxrs
* mvn wildfly-jar:dev (build hollow server and start it)
* mvn package -Ddev (notice the deployment scanner that discover the deployed application)
* Do changes in code.
* mvn package -Ddev (notice the deployment scanner that discover the refreshed deployed application)
* NB: You will have to kill the server, shutdown not yet usable.