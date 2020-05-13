# WildFly bootable jar Https Openshift example

Openshift binary build and deployment
=====================================

Steps:
* mvn package
* mkdir os && cp target/https-wildfly.jar os/
* oc new-build --strategy source --binary --image-stream openjdk11 --name https-test
* oc start-build https-test --from-dir ./os/
* oc new-app https-test
* oc expose svc/https-test
* keytool -genkey -keyalg RSA -alias ks-alias -keystore keystore.jks -validity 360 -keysize 2048
* oc secrets new ks-secret keystore.jks
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

* Access the app: \<route\>/https-test
