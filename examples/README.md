JDK11 required arguments
========================

--add-exports=java.base/sun.nio.ch=ALL-UNNAMED
--add-exports=jdk.unsupported/sun.reflect=ALL-UNNAMED
--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED

Openshift binary build and deployment
=====================================
The JDK11 openshift builder image is used to build/run these example applications

* oc import-image openjdk11 --from=registry.access.redhat.com/openjdk/openjdk-11-rhel7 --confirm

JVM env var required for openshift
===================================

* GC_MAX_METASPACE_SIZE=256
* GC_METASPACE_SIZE=96
* JAVA_OPTS=-Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true


Interesting arguments to pass to JAVA_ARGS when running in Openshift
* -Djboss.node.name=<a node name>
* -b=0.0.0.0

DEBUG
=====
Env variables to enable debug:
* JAVA_DEBUG=true
* JAVA_DEBUG_PORT=8787

Port forwarding
* oc get pods
* oc port-forward <pod name> 8787:8787

Attach your debugger to 127.0.0.1:8787
