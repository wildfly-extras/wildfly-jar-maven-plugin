# Highlight development mode. 

A mode that allows you to build the bootable jar once and to start it, then make changes to your
code. Newly packaged application is reloaded by the running server.

Using 1.0.0.Alpha2 (Deprecated usage in next release)
=====================================================
* cd jaxrs
* mvn wildfly-bootable-jar:package -Dwildfly.bootable.dev.server=true
* mvn wildfly-bootable-jar:start
* mvn package -Dwildfly.bootable.dev.app=true (notice the deployment scanner that discover the deployed application)
* Do changes in code.
* mvn package -Dwildfly.bootable.dev.app=true (notice the deployment scanner that discover the refreshed deployed application)
* NB: You will have to kill the server, shutdown not yet usable.

Using SNAPSHOT
==============
* cd jaxrs
* mvn wildfly-bootable-jar:dev (build hollow server and start it)
* mvn package -Ddev (notice the deployment scanner that discover the deployed application)
* Do changes in code.
* mvn package -Ddev (notice the deployment scanner that discover the refreshed deployed application)
* NB: You will have to kill the server, shutdown not yet usable.