# EJB Persistent Clustered Timers WildFly bootable jar OpenShift example

In this example we are demonstrating EJB clustered timers, specifically persistent ones.

The simplest use case is related to _automatic_ timers in clustered environments. In such a scenario all the running 
application instances will execute the scheduled method, thus breaking the application logic.

_Persistent_ timers can be used in the above case to avoid such behavior in clustered environments. 

The WildFly timer service implementation allows for the user to configure a JDBC based persistent storage for EJB 
timers. This way a given timer can be executed each time by exactly one of the running application instances.    

The example application defines an automatic timer which is executed every 10 seconds.
It requires for one Postgresql instance and two replicas of the example application to be 
provisioned on OpenShift.

# Bootable JAR Maven plugin configuration
High level view of the Bootable JAR Maven plugin configuration

## Galleon feature-packs

* `org.wildfly:wildfly-galleon-pack`
* `org.wildfly:wildfly-datasources-galleon-pack`

## Galleon layers

* `cloud-server`
* `ejb`
* `postgresql-datasource`

## CLI scripts
WildFly CLI scripts executed at packaging time

* [ejb-persistent-clustered-timers.cli](../scripts/ejb-persistent-clustered-timers.cli)

## Extra content
Extra content packaged inside the provisioned server

* None

# Openshift build and deployment
Technologies required to build and deploy this example

* Helm chart for WildFly `wildfly/wildfly`

# Pre-requisites

* You've cloned and built this repo and then `cd` into this 
  [example directory](../../examples/ejb-persistent-clustered-timers)

* You are logged into an OpenShift cluster and have `oc` command in your path.

* You have successfully provisioned a Postgresql service (see the [example steps](#example-steps) below)

* You have installed Helm. Please refer to [Installing Helm page](https://helm.sh/docs/intro/install/) to install Helm in your environment

* You have installed the repository for the Helm charts for WildFly

```shell
$ helm repo add wildfly https://docs.wildfly.org/wildfly-charts/
```

# Example steps

1. Create and configure a PostgreSQL data base server:

```shell
$ oc new-app --name database-server \
     --env POSTGRESQL_USER=admin \
     --env POSTGRESQL_PASSWORD=admin \
     --env POSTGRESQL_DATABASE=sampledb \
     postgresql
```

2. Deploy the example application using WildFly Helm charts

```shell
$ helm install ejb-persistent-clustered-timers -f helm.yaml wildfly/wildfly
```

3. Follow the hints that the `helm install` command printed out, until the two pods are ready.
   
4. You can then have a look at the messages printed by the two pods running the workload, by inspecting their logs.
   First obtain the pod names:
```shell
$ oc get pods
NAME                                              READY   STATUS      RESTARTS   AGE
database-server-57dd6dc965-fvxb4                  1/1     Running     0          5m7s
ejb-persistent-clustered-timers-1-build           0/1     Completed   0          4m16s
ejb-persistent-clustered-timers-cfcf746cb-hpgjp   1/1     Running     0          2m42s
ejb-persistent-clustered-timers-cfcf746cb-t2xnp   1/1     Running     0          2m42s
```

Then open one shell in order to tail the first pod logs, e.g.: 
```shell
$ oc logs -f ejb-persistent-clustered-timers-cfcf746cb-hpgjp
...
10:23:33,984 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0025: WildFly Full 26.0.0.Final (WildFly Core 18.0.0.Final) started in 4373ms - Started 410 of 500 services (213 services are lazy, passive or on-demand)
10:23:33,998 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0060: Http management interface listening on http://0.0.0.0:9990/management
10:23:33,998 INFO  [org.jboss.as] (Controller Boot Thread) WFLYSRV0054: Admin console is not enabled
10:23:50,286 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:23:50 GMT 2022
10:24:00,082 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:24:00 GMT 2022
10:24:10,009 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:24:10 GMT 2022
10:24:50,009 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:24:50 GMT 2022
10:25:00,011 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:25:00 GMT 2022
10:25:10,008 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:25:10 GMT 2022

...
```

Then do the same on a separate shell to inspect the last pod logs:
```shell
$ oc logs -f ejb-persistent-clustered-timers-cfcf746cb-t2xnp
...
10:23:40,011 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:23:40 GMT 2022
10:24:20,007 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:24:20 GMT 2022
10:24:30,007 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:24:30 GMT 2022
10:24:40,007 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:24:40 GMT 2022
10:25:20,008 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:25:20 GMT 2022
10:25:30,224 INFO  [org.wildfly.plugins.demo.ejb.timers.clustered.AutomaticRecurringTimer] (EJB default - 1) This is the actual timer execution, it is Wed Apr 13 10:25:30 GMT 2022
...
```

You'll notice tha each timer is executed only once, by (randomly) one of the two deployed instances.
