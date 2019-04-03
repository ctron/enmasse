#!/usr/bin/env bash
sudo sed -e "/^\[registries.insecure\]$/,/^/s/\(registries = \[\)\(.*\)\(\]\)/\1'${OCP4_EXTERNAL_IMAGE_REGISTRY}',\2\3/" -i /etc/containers/registries.conf
sudo systemctl restart docker
cat /etc/containers/registries.conf
oc new-project amq-online-images
oc policy add-role-to-group system:image-puller system:serviceaccounts:${OPENSHIFT_PROJECT} -n amq-online-images
make imagelist
cat imagelist.txt
TEMPLATES=templates/build/enmasse-${TAG} IMAGE_LIST=./imagelist.txt ./systemtests/scripts/copy-and-rename-images.sh ${DOCKER_REGISTRY} ${PRODUCT_DOCKER_REGISTRY} ${OCP4_EXTERNAL_IMAGE_REGISTRY} ${OCP4_INTERNAL_IMAGE_REGISTRY} amq-online-images