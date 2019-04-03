#!/bin/sh
KEYTAB=$1
KERBEROS_PRINCIPAL=$2
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

echo Kerberos Principal: "${KERBEROS_PRINCIPAL}"
kinit -t ${KEYTAB} "${KERBEROS_PRINCIPAL}"
klist || true


# Need to turn off the default ask behaviour
cat > ~/.ssh/config << EOF
StrictHostKeyChecking no
EOF
chmod 600 ~/.ssh/config

git config --global user.email "amq-online-ci@redhat.com"
git config --global user.name "AMQ Online CI"


