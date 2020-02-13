Openshift binary build and deployment
=====================================
The JDK11 openshift builder image is used to build/run these example applications

* oc import-image openjdk11 --from=registry.access.redhat.com/openjdk/openjdk-11-rhel7 --confirm

Interesting arguments to pass to JAVA_ARGS when running in Openshift
* -Djboss.node.name=<a node name>
* -b=0.0.0.0
