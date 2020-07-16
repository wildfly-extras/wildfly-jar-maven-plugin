Each directory contains a specific use-case in which wildfly bootable jar maven plugin is used. Each example contains a README.md file
that contains the information to build and run the example. NB: The examples depend on the latest released plugin.


JDK11 required arguments
========================

When running on JDK11, the following options should be used:

* --add-exports=java.base/sun.nio.ch=ALL-UNNAMED
* --add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED
* --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED

Openshift binary build and deployment
=====================================
The JDK11 openshift builder image can be used to build/run these example applications. To import the image in openshift:

* oc import-image openjdk11 --from=registry.access.redhat.com/openjdk/openjdk-11-rhel7 --confirm

JVM ENV variable required for Openshift
=======================================

* GC_MAX_METASPACE_SIZE=256
* GC_METASPACE_SIZE=96
* JAVA_OPTS=-Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true

Enable Debug for Openshift
==========================

Env variables to enable debug:
* JAVA_DEBUG=true
* JAVA_DEBUG_PORT=8787

Port forwarding
* oc get pods
* oc port-forward <pod name> 8787:8787

Attach your debugger to 127.0.0.1:8787
