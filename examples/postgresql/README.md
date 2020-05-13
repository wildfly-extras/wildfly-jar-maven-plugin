# Postgresql WildFly bootable jar Openshift example

Openshift binary build and deployment
=====================================

NB: postgresql server must be deployed in the cluster.

Steps: 
* mvn package -Popenshift
* mkdir os && cp target/postgresql-wildfly.jar os/
* oc new-build --strategy source --binary --image-stream openjdk11 --name wf-postgresql
* oc start-build wf-postgresql --from-dir ./os/
* oc new-app wf-postgresql
* oc expose svc/wf-postgresql
* Add the following env variables to wf-postgresql DeploymentConfig:
- POSTGRESQL_USER=admin
- POSTGRESQL_PASSWORD=admin
- POSTGRESQL_SERVICE_HOST=postgresql
- POSTGRESQL_SERVICE_PORT=5432
- POSTGRESQL_DATABASE=sampledb

* Add a task (from POD terminal): curl -X POST http://127.0.0.1:8080/tasks/title/foo
* Access the app: \<route\>/
