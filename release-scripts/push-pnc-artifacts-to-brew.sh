#!/bin/sh
BUILD_ID=$1
BREW_TAG=$2
pnc brew-push build ${BUILD_ID} ${BREW_TAG}

STATUS=`pnc brew-push status ${BUILD_ID} | python -c 'import sys,json; print (json.load(sys.stdin)["status"])'`
while [ "$STATUS" != "SUCCESS" ];
do
    echo "Push status is $STATUS, waiting for SUCCESS"
    sleep 60
    pnc brew-push status ${BUILD_ID}
    STATUS=`pnc brew-push status ${BUILD_ID} | python -c 'import sys,json; print (json.load(sys.stdin)["status"])'`
done
