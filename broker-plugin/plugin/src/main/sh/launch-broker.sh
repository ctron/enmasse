#!/bin/sh

BROKER_DIR=${AMQ_HOME}
BROKER_CUSTOM_DIR=${BROKER_DIR}/custom
sed -i '$ d' ${BROKER_DIR}/bin/launch.sh

source $BROKER_CUSTOM_DIR/bin/env.sh
source ${BROKER_DIR}/bin/launch.sh
CONFIG_TEMPLATES=${BROKER_CUSTOM_DIR}/conf

echo "JAVA_OPTS=$JAVA_OPTS"

sed -i 's/@JAVA_OPTS@/${JAVA_OPTS}/g' ${BROKER_CUSTOM_DIR}/conf/artemis.profile


# Parameters are
# - instance directory
function init_configure() {
    local instanceDir=$1

    mkdir -p ${BROKER_DIR}/conf/
    cp -n $BROKER_CUSTOM_DIR/lib/* $BROKER_DIR/lib/
    cp -n $BROKER_CUSTOM_DIR/conf/* $BROKER_DIR/conf/
}

function update_ssl_certs() {
    local instanceDir=$1
    rm -rf ${TRUSTSTORE_PATH} ${KEYSTORE_PATH} ${AUTH_TRUSTSTORE_PATH} ${EXTERNAL_KEYSTORE_PATH}
    cp -n $BROKER_CUSTOM_DIR/certs/* $instanceDir/etc/
    echo "cp -n $BROKER_CUSTOM_DIR/certs/* $instanceDir/etc/"
}

function startServer() {

  instanceDir="${HOME}/${AMQ_NAME}"
  echo "Configuring the Broker.  Instance: $instanceDir"

  init_configure $instanceDir
  configure $instanceDir

  update_ssl_certs $instanceDir
  exec ${instanceDir}/bin/artemis run
}

startServer $1