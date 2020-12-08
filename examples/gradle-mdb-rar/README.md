# Simple Gradle/MAVEN Bootable Wildfly with a Basic MDB via RAR to an external AMQ 5.x Broker

Example contributed by Fred Welland.

## WHY??
Couple of reasons:

1. Assembling a WildFly Bootable jar is currently geared to Maven only; 
   well and some folks just use gradle or have mature gradle builds & pipelines that make EE style artifacts and don't want to re-engineer 'everything' just for an assembly step.   
2. Up until recently, there wasn't a really strong RAR WildFly Bootable JAR example.  
 
This example does **NOT** get rid of maven; rather it just uses the Maven WildFly Bootable JAR plugin for final bootable
assembly while other core build steps and procedures are done via gradle.  

This example has or does: 

* Very basic MBD class
* RAR deployment into bootable JAR 
* Deployment of MDB Jar into bootable JAR. 
* Tried to make smallest Wildfly Runtime as possible 
* Using boot up properties file for site specific variables  (i.e. connectivity to AMQ broker)
* JDK 8 build and runtime
* Gradle 6.7 via wrapper
* Wildfly 21  & [Galleon/Bootable](https://docs.wildfly.org/21/Bootable_Guide.html)
* Maven Wrapper 

## Building & Running

Maven activities are called from gradle script.  Standard gradle like targets work, so: 

	./gradlew clean build 

will build everything; the resulting JAR is directly runnable.   Create or edit a properties files as per src/main/resources/Leopard.dev.properties

Running from Command Line is simple; set some environment variables and use java -jar.   Example : 

	 java -jar target/gradle-demo-bootable.jar  --properties src/main/resources/Leopard.dev.properties 

Toss (by default tcp:localhost:localhost:61616) a text message at the targe AMQ queue 'simpleMDBTestQueue' and then check server log output. 

For example:

```
activemq producer --destination queue://simpleMDBTestQueue --message Hello --messageCount 1
```

## Notes & Other Interesting Things

### Uses 'vanilla' EE MDB source code
This example doesn't use a WF/JB specific deployment descriptor or WF/JBOSS api or
annotation to wire up the MDB to a RAR.   Rather it sets the default resource adapter
for the EJB subsystem to use the deployed RAR. 

### Uses Properties during WF CLI Configuration
Nothing that magical: the CLI config script (src/main/resources/deploy-amq-rar.cli) 
externalizes some values so that a properties files can have location and 
credentials for an AMQ broker.  See also src/main/resources/Leopard.dev.properties

### This is single project example
Primarly due to how simple it is; 'everything' was just collapsed into a single 
gradle project.  A more structured approach could be to setup a gradle project
with sub-projects, perhaps like so: 

	root
		mdb     - contains java source for MDB
		ear     - simple assembly module to build ear
		wildfly - isolate mvn/wfbootable stuff here

### Not so Hollow jar
The MVN assemblage is marked as 'hollow', but notice that the CLI session deploys 
two things: 

1. the AMQ rar
1. the gradle built, vanilla MDB jar

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