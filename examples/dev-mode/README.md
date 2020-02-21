# Highlight development mode. 

A mode that allows you to build the bootable jar once, start it, then make changes to your
code. Newly deployed application being discovered reloaded in running server.

* cd jaxrs
* mvn wildfly-bootable-jar:package -Dwildfly.bootable.dev.server=true
* mvn wildfly-bootable-jar:start
* mvn package -Dwildfly.bootable.dev.app=true (notice the deployment scanner that discover the deployed application)
* Do changes in code.
* mvn package -Dwildfly.bootable.dev.app=true (notice the deployment scanner that discover the refreshed deployed application)

