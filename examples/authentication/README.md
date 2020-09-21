# WildFly bootable jar Authentication example

The files _extra-content/standalone/configuration/bootable-users.properties_ and _extra-content/standalone/configuration/bootable-groups.properties_ are packaged inside the bootable jar during packaging. CLI script adds a new security realm and domain referencing these properties file and maps the default security domain in undertow to the newly added domain.

**Note: This example is to illustrate the steps to enable authentication, an application should not be making use of clear text properties files for authentication in production.**

Build and run
=============

* To build: mvn package
* To run: mvn wildfly-jar:run

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
