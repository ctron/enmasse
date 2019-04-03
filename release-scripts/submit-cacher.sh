#!/bin/sh
VERSION=$1
MAVEN_VERSION=$2
set -e

JAR_ARTIFACTS="api-server keycloak-controller mqtt-gateway mqtt-lwt sasl-plugin service-broker standard-controller topic-forwarder"
ZIP_ARTIFACTS="address-space-controller agent broker-plugin"

for jart in $JAR_ARTIFACTS
do
    url="http://download.eng.bos.redhat.com/brewroot/packages/io.enmasse-enmasse/${VERSION}/1/maven/io/enmasse/${jart}/${MAVEN_VERSION}/${jart}-${MAVEN_VERSION}.jar"
    curl -X POST -F "url=$url" http://ce-cacher.usersys.redhat.com/fetch/
done

for zart in $ZIP_ARTIFACTS
do
    url="http://download.eng.bos.redhat.com/brewroot/packages/io.enmasse-enmasse/${VERSION}/1/maven/io/enmasse/${zart}/${MAVEN_VERSION}/${zart}-${MAVEN_VERSION}-dist.zip"
    curl -X POST -F "url=$url" http://ce-cacher.usersys.redhat.com/fetch/
done
