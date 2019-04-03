#!/bin/bash
BUILD_CONFIG=$1
BUILD_INFO=`pnc build --rebuild-mode FORCE -n ${BUILD_CONFIG}`
if [ $? -gt 0 ]; then
    echo "Error starting build build"
    exit 1
fi
BUILD_ID=`echo $BUILD_INFO | python -c 'import sys,json; print (json.load(sys.stdin)["id"])'`
echo "Build id: $BUILD_ID"
record=`pnc get-build-record ${BUILD_ID}`
while [ $? -gt 0 ];
do
    echo "Waiting for build-record to appear"
    sleep 60
    record=`pnc get-build-record ${BUILD_ID}`
done
echo $record > build_record.json
echo $BUILD_ID > build_id.txt
echo $record | python -c 'import sys,json; print (json.load(sys.stdin)["execution_root_version"])' > scm_revision.txt

STATUS=`echo $record | python -c 'import sys,json; print (json.load(sys.stdin)["status"])'`
if [ "$STATUS" == "DONE" ]
then
    echo "Build completed successfully"
    echo "Build record: ${record}"
    exit 0
else
    echo "Build failed with status ${STATUS}"
    echo "Build record: ${record}"
    exit 1
fi
