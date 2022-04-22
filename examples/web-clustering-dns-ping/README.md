# Web session sharing between pod instances example

In this example we are provisioning a WildFly Bootable JAR containing a simple servlet.
Multiple replicas of the deployment are created, all sharing the same web sessions.

# WildFly Bootable JAR Maven plugin configuration
High level view of the WildFly Maven plugin configuration

## Galleon feature-packs

* `org.wildfly:wildfly-galleon-pack`

## Galleon layers

* `web-server`
* `web-clustering`

## CLI scripts
WildFly CLI scripts executed at packaging time

* A [CLI script](../scripts/os-clustering-dns-ping.cli) to enable `dns.DNS_PING` protocol and configure the Ping service name 
thanks to the `OPENSHIFT_DNS_PING_SERVICE_NAME` env variable that Helm Chart for WildFly generates.

## Extra content
Extra content packaged inside the provisioned server

* None

# Openshift build and deployment
Technologies required to build and deploy this example

* Helm chart for WildFly `wildfly/wildfly`.

# Pre-requisites

* You are logged into an OpenShift cluster and have `oc` command in your path

* You have installed Helm. Please refer to [Installing Helm page](https://helm.sh/docs/intro/install/) to install Helm in your environment

* You have installed the repository for the Helm charts for WildFly

 ```
helm repo add wildfly https://docs.wildfly.org/wildfly-charts/
```

# Example steps

1. Deploy the example application using WildFly Helm charts

```
helm install web-clustering-dns-ping-app -f helm.yaml wildfly/wildfly
```

2. Your replicas are now sharing the web session. You can access the application, note the Session ID then scale/un-scale replicas, the session ID is preserved.