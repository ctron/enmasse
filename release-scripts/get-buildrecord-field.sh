#!/bin/sh
FIELD=$1
RECORD=$2
cat $RECORD | python -c "import sys,json; print (json.load(sys.stdin)[\"$FIELD\"]")
