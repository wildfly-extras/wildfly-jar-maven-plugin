# [DEPRECATED] Keycloak WildFly bootable jar example

The example `../elytron-oidc-client` highlight the supported way to secure a deployment using OpenID Connect (OIDC).

Usage of the Keycloak Galleon feature-pack highlighted in this example is deprecated.

Build a bootable JAR containing an application secured with Keycloak.
In order to enable keycloak, you must use the Galleon layer `keycloak-client-oidc` that brings in the 
OIDC keycloak subsystem. This subsystem is required for WildFly to secure deployments using keycloak.

In order to declare the deployment to be secured with keycloak, the CLI script `../scripts/configure-oidc.cli` is called during packaging.

Initial Steps
=======

* Download the keycloak server from: `https://www.keycloak.org/download`
* Start the keycloak server to listen on port 8090: `keycloak/bin/standalone.sh -Djboss.socket.binding.port-offset=10`
* Log into the keycloak server admin console (you will possibly be asked to create an initial admin user) : `http://127.0.0.1:8090/`
* Create a Realm named `WildFly`
* Create a Role named `Users`
* Create a User named `demo`, password `demo`
* Assign the role `Users` to the user `demo`
* Create a Client named `simple-webapp` with Root URL: `http://127.0.0.1:8080/simple-webapp`

Build and run
========

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`
* Access the application: `http://127.0.0.1:8080/simple-webapp`
* Access the secured servlet.
* Log-in using the `demo` user, `demo` password (that you created in the initial steps).
* You should see a page containing the Principal ID.