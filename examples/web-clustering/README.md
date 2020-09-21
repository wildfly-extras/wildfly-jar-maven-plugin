# Web session sharing between multiple bootable JAR instances example

An example of a clustering application that enables HTTP session data caching using the WildFly distributable session manager.

Build and run
============= 

* To build: `mvn package`
* To run first instance: `java -jar target/web-clustering-bootable.jar -Djboss.node.name=node1`
* To run second instance: `java -jar target/web-clustering-bootable.jar -Djboss.node.name=node2 -Djboss.socket.binding.port-offset=10`
* Access the application running in the first instance: http://127.0.0.1:8080
* Note sessionID and user creation time.
* Kill first instance
* Access the application running in the second instance: http://127.0.0.1:8090
* SessionID and user creation time should be the same as you previously noted.

OpenShift binary build and deployment
=====================================

* mvn package -Popenshift
* mkdir os && cp target/web-clustering-bootable.jar os/
* oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default
* oc import-image ubi8/openjdk-11 --from=registry.redhat.io/ubi8/openjdk-11 --confirm
* oc new-build --strategy source --binary --image-stream openjdk-11 --name web-clustering
* oc start-build web-clustering --from-dir ./os/
* oc new-app web-clustering -e KUBERNETES_NAMESPACE=$(oc project -q) -e JGROUPS_CLUSTER_PASSWORD=mypassword
    * KUBERNETES_NAMESPACE env variable is required to see other pods in the project, otherwise the server attempts to retrieve pods from the 'default' namespace that is not the one our project is using.
    * JGROUPS_CLUSTER_PASSWORD env variable is expected by the server in order to establish authenticated communication between servers.
* oc expose svc/web-clustering
* Get the application route host name and access via web browser, note the user created time, session ID and host name, which identify the pod that generated the response.
* Scale the application to 2 pods: oc scale --replicas=2 deployments web-clustering
* List pods: oc get pods
* Kill the oldest POD (that answered the first application request): oc delete pod web-clustering-1-r4cx8
* Access the application again, you will notice that created time and session ID are the same but retrieved from a different pod.