# MP Config WildFly bootable jar example
To build: mvn package
To run: mvn wildfly-bootable-jar:run
Access the application: http://127.0.0.1:8080/test-config

Openshift binary build and deployment
=====================================

Steps:
* mvn package -Popenshift
* mkdir os && cp target/mp-config-wildfly-bootable.jar os/
* oc new-build --strategy source --binary --image-stream openjdk11 --name mp-config
* oc start-build mp-config --from-dir ./os/
* oc new-app mp-config
* oc expose svc/mp-config
* Create a config map: oc create configmap mp-config --from-literal=a=Openshit1 --from-literal=b=Openshift2
* Mount the config map on /etc/config in the pod, update DeploymentConfig:
  spec:
    volumes:
        - name: config-volume
          configMap:
            name: mp-config
    containers:
        volumeMounts:
            - name: config-volume
              mountPath: /etc/config

* Access the app: <route>/test-config
