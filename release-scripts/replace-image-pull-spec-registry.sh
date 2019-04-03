#!/bin/sh
REGISTRY=$1
MATCH=$2
TEMPLATES=$3

for i in `find ${TEMPLATES} -name "*.yaml"`
do
    sed -e "s,registry.redhat.io/${MATCH},${REGISTRY}/${MATCH},g" -i $i
done
