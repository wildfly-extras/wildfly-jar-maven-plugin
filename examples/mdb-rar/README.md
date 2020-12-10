# WildFly MDB and RAR deployment application example

This example shows how to package an activemq resource adapter in order for an 
MDB to consume messages emitted by the activemq server. 

Start the activemq server
=========================

* Download the server from https://activemq.apache.org/components/classic/download/
* Start the server: `bin/activemq start`
 
Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`

Produce Messages from the activemq CLI
======================================

* `bin/activemq producer --destination queue://java:jboss/activemq/queue/LocalRPCRequest`
* You will notice that the MDB prints the received messages in the console.