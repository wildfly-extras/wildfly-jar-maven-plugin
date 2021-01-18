#!/bin/bash

if [ "x$WILDFLY_BOOTABLE_HOME" = "x" ]; then
    WILDFLY_BOOTABLE_HOME="/opt/wildfly-bootable/wildfly"
fi

if [ "x$WILDFLY_BOOTABLE_JAR" = "x" ]; then
    WILDFLY_BOOTABLE_JAR="/opt/wildfly-bootable/wildfly-bootable.jar"
fi

BIND_ADRESS=$2

if [ "x$BIND_ADRESS" = "x" ]; then
    BIND_ADRESS="0.0.0.0"
fi

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

"$JAVA" -jar "$WILDFLY_BOOTABLE_JAR" -b=$BIND_ADRESS --install-dir="$WILDFLY_BOOTABLE_HOME"
