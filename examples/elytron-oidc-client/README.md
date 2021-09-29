# Elytron OpenID Connect (OIDC) client, WildFly bootable jar example

Starting WildFly 25, we have removed the need for the `org.keycloak:keycloak-adapter-galleon-pack` Galleon feature-pack in order to 
secure a deployment with OIDC. WildFly offers a native support for OIDC that is highlighted by this example.

We are relying on the `elytron-oidc-client` subsystem that has been introduced in WildFly 25. This subsystem allows to interact with OIDC compatible 
authorization servers. In this example we are interacting with a Keycloak authorization server.

The WildFly Galleon layer `elytron-oidc-client` brings in the `elytron-oidc-client` subsystem to the server configuration. This layer,
provisioned with the `web-server` Galleon layer, produces a server containing the features required to run a Servet secured with OIDC. 

In this example we have chosen to embed the security configuration in the deployment. The descriptor file `WEB-INF/oidc.json` contains the configuration 
required to secure the deployment. This file contains the expression `${org.wildfly.bootable.jar.example.oidc.provider-url}` 
(a references to the `org.wildfly.bootable.jar.example.oidc.provider-url` system property) for the provider URL value. 
This allows you to adjust this URL according to your execution context.

NB: In order for the deployment to be identified as "secured with OIDC", the `auth-method` value in `WEB-INF/web.xml` file must be set to `OIDC`.

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
* To run: `java -jar target/simple-webapp-bootable.jar -Dorg.wildfly.bootable.jar.example.oidc.provider-url="http://localhost:8090/auth/realms/WildFly"`
* Access the application: `http://127.0.0.1:8080/simple-webapp`
* Access the secured servlet.
* Log-in using the `demo` user, `demo` password (that you created in the initial steps).
* You should see a page containing the Principal ID.