# Simple Gradle/MAVEN Bootable Wildfly with a Basic MDB via RAR to an external Kafka Broker

Example contributed by Fred Welland and enriched by Emmanuel Hugonnet

=======

## WHY??
Couple of reasons:

1. Assembling a WildFly Bootable jar is currently geared to Maven only; 
   well and some folks just use gradle or have mature gradle builds & pipelines that make EE style artifacts and don't want to re-engineer 'everything' just for an assembly step.   
2. Up until recently, there wasn't a really strong RAR WildFly Bootable JAR example.  

While this example can be used as a fully Apache Maven project you can also use Gradle to build it.
It does **NOT** get rid of Apache Maven; rather it just uses the Maven WildFly Bootable JAR plugin for final bootable
assembly while other core build steps and procedures are done via Gradle.

This example has or does: 

* A very basic MBD to consume messages.
* A single REST endpoint to send messages (to be consummed by the MDB).
* RAR deployment into bootable JAR 
* A war deployment of the MDB and REST Endpoint into bootable JAR. 
* Tried to make smallest Wildfly Runtime as possible.
* JDK 11 build and runtime
* Gradle 8.7 via wrapper
* Wildfly 32  & [Galleon/Bootable](https://docs.wildfly.org/32/Bootable_Guide.html)
* Maven Wrapper.

## Starting Kafka.

This project provides a `docker-compose.yaml` file to be used with docker-compose to start a simple kafka broker. 
To start itexecute the following command:

	docker-compose up

## Building & Running

Maven activities are called from gradle script.  Standard gradle like targets work, so: 

	./gradlew clean build 

will build everything; the resulting JAR is directly runnable with the command:

	java -jar target/gradle-demo-bootable.jar

To send a message got to:

	http://localhost:8080/gradle-demo/rest/kafka

You should see the proper traces in the server logs.

## Notes & Other Interesting Things

### Uses 'vanilla' EE MDB source code
This example uses a WF/JB specific deployment descriptor to be able to send messages to Kafka.
It also sets the default resource adapter for the EJB subsystem to use the deployed RAR.

### This is single project example
Primarly due to how simple it is; 'everything' was just collapsed into a single 
Gradle project.  A more structured approach could be to setup a Gradle project
with sub-projects, perhaps like so: 

	root
		mdb     - contains java source for MDB
		ear     - simple assembly module to build ear
		wildfly - isolate mvn/wfbootable stuff here

### Not so Hollow jar
The MVN assemblage is marked as 'hollow', but notice that the CLI session deploys 
two things: 

1. the Kafka rar
1. the Gradle built, vanilla MDB and REST endpoint war.

The story behind this, is that would be nice to actually 'do' a proper hollowed
bootable.  However, if you do a cli deploy, in this case of the RAR; you cannot 
use the --deploy command line switch on the resulting bootable.jar; WFBootable 
objects and says there is already a deployment.  That said, you can just do a 
ear/war/jar deploy in the CLI, along with the RAR deploy; and it works.  

The side affect of this, is there is a small coupling with the CLI script and the 
gradle artifact build: they need to agree on the artifact name.   

There are probably a few different ways to work around this:  

* perhaps rather than deploy a rar; have it installed as a WF module; and then pivot back to pure hollow bootable
* perhaps pass in an env or property that 'links' the gradle artifact to the MVN build or cli properties