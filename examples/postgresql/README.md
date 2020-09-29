# Postgresql WildFly bootable jar OpenShift example

Openshift binary build and deployment
=====================================

Create and configure a PostgreSQL data base server:

```
oc new-app --name database-server \
     --env POSTGRESQL_USER=admin \
     --env POSTGRESQL_PASSWORD=admin \
     --env POSTGRESQL_DATABASE=sampledb \
     postgresql
```

* mvn package -Popenshift
* mkdir os && cp target/postgresql-bootable.jar os/
* Import the OpenJDK 11 image to run the Java application, create the image stream and deployment:
```
oc import-image ubi8/openjdk-11 --from=registry.redhat.io/ubi8/openjdk-11 --confirm

oc new-build --strategy source --binary --image-stream openjdk-11 --name wf-postgresql

oc start-build wf-postgresql --from-dir ./os/

oc new-app --name wf-postgresql-app \
    --env POSTGRESQL_USER=admin \
    --env POSTGRESQL_PASSWORD=admin \
    --env POSTGRESQL_SERVICE_HOST=database-server \
    --env POSTGRESQL_SERVICE_PORT=5432 \
    --env POSTGRESQL_DATABASE=sampledb \
    --env GC_MAX_METASPACE_SIZE=256 \
    --env GC_METASPACE_SIZE=96 \
    wf-postgresql

oc expose svc/wf-postgresql-app
```
* Add a new task:
```
curl -X POST http://$(oc get route wf-postgresql-app --template='{{ .spec.host }}')/tasks/title/foo`

* Get all the tasks:
`curl http://$(oc get route wf-postgresql-app --template='{{ .spec.host }}')`
