#!/usr/bin/env bash
#script for deploy and setup kubernetes
#parameters:
# {1} path to folder with installation scripts, roles,... (usually templates/install)
# {2} url to OpenShift origin client
# {3} url to minikube
# {4} url to kubectl

SYSTEMTESTS_DIR=${1}
OPENSHIFT_CLIENT_URL=${2:-"https://github.com/openshift/origin/releases/download/v3.7.0/openshift-origin-client-tools-v3.7.0-7ed6862-linux-64bit.tar.gz"}
MINIKUBE_RELEASE_URL=${3:-"https://storage.googleapis.com/minikube/releases/v0.30.0/minikube-linux-amd64"}
KUBECTL_RELEASE_URL=${4:-"https://storage.googleapis.com/kubernetes-release/release/v1.11.8/bin/linux/amd64/kubectl"}
ansible-playbook ${SYSTEMTESTS_DIR}/ansible/playbooks/environment.yml \
    --extra-vars "{\"openshift_client_url\": \"${OPENSHIFT_CLIENT_URL}\", \"minikube_url\": \"${MINIKUBE_RELEASE_URL}\", \"kubectl_url\": \"${KUBECTL_RELEASE_URL}\"}" \
    -t openshift,kubectl,minikube

export CHANGE_MINIKUBE_NONE_USER=true

# install compatible docker version (17.03.x)

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
sudo apt-get update
sudo apt-get -y --allow-downgrades -o Dpkg::Options::="--force-confnew" install docker-ce=17.03.3~ce-0~ubuntu-$(lsb_release -cs)

# install crictl to work around broken kubeadm check

curl -Lo crictl.tar.gz https://github.com/kubernetes-sigs/cri-tools/releases/download/v1.11.1/crictl-v1.11.1-linux-amd64.tar.gz && tar xzf crictl.tar.gz && chmod +x crictl && sudo mv crictl /usr/local/bin/

sudo sh -c 'sed -e 's/journald/json-file/g' -i /etc/docker/daemon.json'
sudo service docker restart && sleep 20

# start local registry

docker run -d -p 5000:5000 registry

sudo mount --make-rshared /

minikube config set WantReportErrorPrompt false
sudo minikube start --vm-driver=none --bootstrapper=kubeadm --kubernetes-version v1.11.8 --insecure-registry localhost:5000 --extra-config=apiserver.authorization-mode=RBAC

minikube update-context
sudo minikube addons enable default-storageclass

kubectl taint node minikube node-role.kubernetes.io/master-
kubectl create clusterrolebinding add-on-cluster-admin --clusterrole=cluster-admin --serviceaccount=default:default

# Wait for k8s to be ready
JSONPATH='{range .items[*]}{@.metadata.name}:{range @.status.conditions[*]}{@.type}={@.status};{end}{end}'; until kubectl get nodes -o jsonpath="$JSONPATH" 2>&1 | grep -q "Ready=True"; do sleep 1; done
kubectl cluster-info
