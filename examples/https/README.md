# WildFly bootable jar HTTPS example

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
* oc new-build --strategy source --binary --image-stream openjdk11 --name https-test
* oc start-build https-test --from-dir ./os/
* oc new-app https-test
* oc expose svc/https-test
* oc secrets new ks-secret extra-content/standalone/configuration/keystore.jks
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
