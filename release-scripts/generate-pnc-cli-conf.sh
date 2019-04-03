#!/bin/sh
CLIENT_ID=$1
CLIENT_SECRET=$2
cat <<EOF
[PNC]
pncurl = http://orch.psi.redhat.com
keycloakurl = https://secure-sso-newcastle.psi.redhat.com

 
keycloakrealm = pncredhat
keycloakclientid = ${CLIENT_ID}
username = ${CLIENT_ID}
password = ${CLIENT_SECRET}
 
useClientAuthorization = true
clientSecret = ${CLIENT_SECRET}
EOF
