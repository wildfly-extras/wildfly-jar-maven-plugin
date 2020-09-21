# WildFly bootable JAR JKube example

This is a project to use Eclipse JKube plugin to build and deploy a bootable JAR on OpenShift.

JKube documentation can be found on https://www.eclipse.org/jkube/

JKube build and deployment for OpenShift
========================================

The following command will create and deploy the Bootable JAR on OpenShift:

`mvn oc:deploy`

Make sure you are logged in to your OpenShift Cluster before you try build/deploy