# WildFly bootable jar Authentication example

**Note: This example is to illustrate the steps to enable authentication, an application should not be making 
use of clear text properties files for authentication in production.**

You can build a pre-configured JAR or a JAR that gets configured at runtime.

Build and run a pre-configured JAR
=====================

The files _extra-content/standalone/configuration/bootable-users.properties_ and 
_extra-content/standalone/configuration/bootable-groups.properties_ are packaged inside the bootable jar 
during packaging. CLI script adds a new security realm and domain referencing these properties file and 
maps the default security domain in undertow to the newly added domain.

NB: In this case the bootable JAR packages the properties files and applies the CLI script to configure security during build.

* To build: mvn clean package
* To run: mvn wildfly-jar:run

Or build and run a JAR that configures security at runtime
===================================

In this case the Bootable JAR is not configured during build and no properties file get packaged in the JAR.
The configuration is done at runtime using a CLI script provided as a launch argument. The properties files 
are retrieved from the current directory. NB: By updating the `runtime-authentication.cli` script the properties files 
could be located in the location of your choice.

* To build: mvn clean package -Pruntime-config
* Copy the properties file in the current directory: cp  extra-content/standalone/configuration/*.properties .
* To run: java -jar target/authentication-bootable.jar --cli-script=runtime-authentication.cli

Test the application
============

First call the servlet without credentials:

curl -v http://localhost:8080/hello

The response will have a HTTP 401 Status

````
HTTP/1.1 401 Unauthorized
...
WWW-Authenticate: Basic realm="Example Realm"
````

Next call with credentials:

curl -v -u testuser:bootable_password  http://localhost:8080/hello

The HTTP status will now be 200 with a response message:

````
HTTP/1.1 200 OK
....
Hello testuser
````
