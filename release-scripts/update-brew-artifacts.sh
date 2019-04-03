#!/bin/sh
RELEASE_VERSION=$1
BUILD_VERSION=$2
MAVEN_VERSION=$3
CE_REPO_DIR=$4

set -e

mkdir -p artifacts
JAR_ARTIFACTS="api-server mqtt-gateway mqtt-lwt sasl-plugin service-broker topic-forwarder none-authservice iot-tenant-service iot-auth-service iot-http-adapter iot-mqtt-adapter iot-device-registry-file"
ZIP_ARTIFACTS="address-space-controller standard-controller agent plugin controller-manager iot-gc iot-proxy-configurator console-init console-httpd"

function get_image_name() {
    local artname=$1
    if [ "$artname" == "sasl-plugin" ]; then
        echo -n "auth-plugin"
    elif [ "$artname" == "plugin" ]; then
        echo -n "broker-plugin"
    elif [ "$artname" == "none-authservice" ]; then
        echo -n "none-auth-service"
    else
        echo -n $artname
    fi
}

for jart in $JAR_ARTIFACTS
do
    url="http://download.eng.bos.redhat.com/brewroot/packages/io.enmasse-enmasse/${BUILD_VERSION}/1/maven/io/enmasse/${jart}/${MAVEN_VERSION}/${jart}-${MAVEN_VERSION}.jar"
    echo "Downloading $url"
    file="${jart}-${MAVEN_VERSION}.jar"
    curl -o artifacts/${file} $url
    image_name=`get_image_name $jart`
    file_md5=`md5sum < artifacts/${file}`
    make -C ${CE_REPO_DIR}/${image_name} ARTIFACT=${file} ARTIFACT_MD5=${file_md5} RELEASE_VERSION=${RELEASE_VERSION} generate
done

for zart in $ZIP_ARTIFACTS
do
    url="http://download.eng.bos.redhat.com/brewroot/packages/io.enmasse-enmasse/${BUILD_VERSION}/1/maven/io/enmasse/${zart}/${MAVEN_VERSION}/${zart}-${MAVEN_VERSION}-dist.zip"
    echo "Downloading $url"
    file="${zart}-${MAVEN_VERSION}-dist.zip"
    curl -o artifacts/${file} $url
    image_name=`get_image_name $zart`
    file_md5=`md5sum < artifacts/${file}`
    make -C ${CE_REPO_DIR}/${image_name} ARTIFACT=${file} ARTIFACT_MD5=${file_md5} RELEASE_VERSION=${RELEASE_VERSION} generate
done

pushd artifacts
md5sum * > ../artifacts.md5sum
popd
