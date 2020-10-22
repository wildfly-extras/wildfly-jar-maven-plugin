# WildFly MDB and RAR deployment application example

This example shows how to package an activemq resource adapter in order for an 
MDB to consume messages emitted by the activemq server. 

Start the activemq server
=========================

* Download the server from https://activemq.apache.org/components/classic/download/
* Start the server: `bin/activemq start`

Download the rar file
=====================

* It is available in Maven repository: `https://search.maven.org/search?q=a:activemq-rar`
* Copy the rar in the current directory as `activemq-rar.rar`.
* NB: the script `deploy-rar.cli` is called when building the bootable JAR to deploy the rar archive.
 
Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`

Produce Messages from the activemq CLI
======================================

* `bin/activemq producer --destination queue://java:jboss/activemq/queue/LocalRPCRequest`
* You will notice that the MDB prints the received messages in the console.