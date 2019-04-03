#!/usr/bin/env bash

SEARCH=${1}

for hash in `oc get image | grep ${SEARCH} | awk '{print $1}'`
do
    oc delete image ${hash}
done
