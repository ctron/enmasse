#!/bin/sh
KEYTAB=$1
mkdir -p ${HOME}/.cekit
cat<<EOF > ${HOME}/.cekit/config
[common]
redhat = True
ssl_verify = False

[repositories]
jboss-os = http://git.app.eng.bos.redhat.com/git/jboss-container-tools.git/plain/repos/jboss-rhel-os.repo
jboss-ocp = http://git.app.eng.bos.redhat.com/git/jboss-container-tools.git/plain/repos/jboss-rhel-ocp.repo
jboss-rhscl = http://git.app.eng.bos.redhat.com/git/jboss-container-tools.git/plain/repos/jboss-rhel-rhscl.repo
EOF

kinit -t ${KEYTAB} host/amq-online-ci@REDHAT.COM
klist || true
