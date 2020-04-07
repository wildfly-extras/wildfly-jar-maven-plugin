# byteman java agent usage

Trace enter and exit of doGet method. In order to resolve byteman classes, you must 
add -Djboss.modules.system.pkgs=org.jboss.byteman when launching the bootable jar.

* Download and install byteman
* cd jaxrs
* mvn clean package
* java -javaagent:${BYTEMAN_HOME}/lib/byteman.jar=script:../byteman/byteman.btm -Djboss.modules.system.pkgs=org.jboss.byteman -jar target/jaxrs-wildfly.jar
* Access to the application: http://127.0.0.1:8080/hello
* Instrumented class logs are printed.
