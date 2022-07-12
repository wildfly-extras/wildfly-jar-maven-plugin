FROM registry.access.redhat.com/ubi8/openjdk-11:latest
COPY --chown=jboss:root target/*.jar /deployments/.
RUN chmod -R ug+rwX /deployments/.
