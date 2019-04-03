#!/usr/bin/env bash

SRC_REGISTRY=${1}
SRC_PRODUCT_REGISTRY=${2}
DST_REGISTRY=${3}
REGISTRY_REPLACEMENT=${4}
DST_DOCKER_ORG=${5}

docker login ${DST_REGISTRY} -u $(oc whoami) -p $(oc whoami -t)

for image in `cat ${IMAGE_LIST}`
do
    SRC_IMAGE_REGISTRY=${SRC_REGISTRY}
    if [[ $image == *"amq7/amq-interconnect"* || $image == *"amq-broker-7"* ]]; then
        SRC_IMAGE_REGISTRY=${SRC_PRODUCT_REGISTRY}
    fi 
    src_image=$(echo ${image} | sed -e "s#${DOCKER_REGISTRY}#${SRC_IMAGE_REGISTRY}#g")
    dst_image=$(echo ${image} | sed -e "s#${DOCKER_REGISTRY}/\(${DOCKER_ORG}/\|${DOCKER_ORG_PREVIEW}/\|amq7/\|amq-broker-7/\)#${DST_REGISTRY}/${DST_DOCKER_ORG}/#g")

    echo "Copying ${src_image} to ${dst_image}"

    docker pull ${src_image}
    docker tag ${src_image} ${dst_image}
    docker push ${dst_image}

done

docker images | grep ${DST_REGISTRY}/${DST_DOCKER_ORG}

for i in `find ${TEMPLATES} -name "*.yaml"`
do
    sed -e "s,${DOCKER_REGISTRY}/amq7/,${REGISTRY_REPLACEMENT}/${DST_DOCKER_ORG}/,g" -i $i
    sed -e "s,${DOCKER_REGISTRY}/amq7-tech-preview/,${REGISTRY_REPLACEMENT}/${DST_DOCKER_ORG}/,g" -i $i
    sed -e "s,${SRC_PRODUCT_REGISTRY}/amq7/,${REGISTRY_REPLACEMENT}/${DST_DOCKER_ORG}/,g" -i $i
    sed -e "s,${SRC_PRODUCT_REGISTRY}/amq-broker-7/,${REGISTRY_REPLACEMENT}/${DST_DOCKER_ORG}/,g" -i $i
done