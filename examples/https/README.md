# WildFly bootable jar HTTPS examples

Example 1, Self-signed certificate
====================

Starting WildFly 25, the galleon layer `undertow-https` can be use to provision a server containing an `https-listener` 
that generates a self-signed certificate.

A CLI script is executed during packaging to disable HTTP undertow listener.

Build and run
========

* To build: `mvn package -Pself-signed`
* To run: `mvn wildfly-jar:run`
* Open the browser and navigate to: `https://127.0.0.1:8443/hello`
* Your browser should warn you about the certificate. Accept the certificate.


Example 2, packaged keystore inside the Bootable JAR
=================================

The file _extra-content/standalone/configuration/keystore.jks_ is packaged inside the bootable jar during packaging.
CLI script enables HTTPS undertow listener and disables HTTP undertow listener.

Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`
* Open the browser and navigate to: `https://127.0.0.1:8443/hello`

Openshift binary build and deployment
=====================================

In this example, the keystore is not packaged in the bootable jar but mounted in the pod.

Steps:
* mvn package -Popenshift
* mkdir os && cp target/https-bootable.jar os/
oc import-image ubi8/openjdk-17 --from=registry.redhat.io/ubi8/openjdk-17 --confirm
* oc new-build --strategy source --binary --image-stream openjdk-17 --name https-test
* oc start-build https-test --from-dir ./os/
* oc new-app https-test
* Create a secure route with Termination Type `passthrough`
* oc create secret generic ks-secret --from-file=extra-content/standalone/configuration/keystore.jks
* Mount the keystore secret on /etc/wf-secrets in the pod, update DeploymentConfig:
  spec:
    volumes:
        - name: secret-volume
          secret:
            secretName: ks-secret
    containers:
        volumeMounts:
            - name: secret-volume
              mountPath: /etc/wf-secrets

* Access the app: \<route\>/hello
