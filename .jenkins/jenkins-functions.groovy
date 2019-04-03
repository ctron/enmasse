def storeArtifacts(String artifactDir) {
    sh(script: "./systemtests/scripts/store_kubernetes_info.sh '${artifactDir}/openshift-info/' || true")
    sh(script: "./systemtests/scripts/collect_logs.sh '/tmp/testlogs' '${artifactDir}/openshift-logs' || true")
    sh(script: 'rm -rf /tmp/testlogs')
}

def installOCclient() {
    echo "install oc client"
    sh(script: './systemtests/scripts/install-oc-client.sh')
}

def makeLinePlot() {
    plot csvFileName: 'duration_sum_report.csv',
            csvSeries: [[
                                file            : 'artifacts/openshift-logs/logs/timeMeasuring/duration_sum_report.csv',
                                exclusionValues : '',
                                displayTableFlag: false,
                                inclusionFlag   : 'OFF',
                                url             : '']],
            group: 'TimeReport',
            title: 'Sum of test operations',
            style: 'line',
            exclZero: false,
            keepRecords: false,
            logarithmic: false,
            numBuilds: '',
            useDescr: false,
            yaxis: '',
            yaxisMaximum: '',
            yaxisMinimum: ''
}

def makeStackedPlot() {
    plot csvFileName: 'duration_sum_report.csv',
            csvSeries: [[
                                file            : 'artifacts/openshift-logs/logs/timeMeasuring/duration_sum_report.csv',
                                exclusionValues : '',
                                displayTableFlag: false,
                                inclusionFlag   : 'OFF',
                                url             : '']],
            group: 'TimeReport',
            title: 'Sum of test operations (stacked)',
            style: 'stackedArea',
            exclZero: false,
            keepRecords: false,
            logarithmic: false,
            numBuilds: '',
            useDescr: false,
            yaxis: '',
            yaxisMaximum: '',
            yaxisMinimum: ''
}

def runSystemtests(String profile, String testCases) {
    sh(script: "./systemtests/scripts/run_test_component.sh '${profile}' '${testCases}'")
}

def startOpenshift() {
    sh(script: './systemtests/scripts/setup-openshift.sh "systemtests"')
    sh(script: 'sudo chmod -R 777 /var/lib/origin/ || true')
}

def waitUntilAgentReady() {
    sh(script: "./systemtests/scripts/wait_until_agent_ready.sh /tmp/agent_ready")
}

def buildEnmasse() {
    sh(script: 'make')
    sh(script: 'make docker_build docker_tag')
}

def postGithubPrComment(def file) {
    echo "Posting github comment"
    def repository_url = scm.userRemoteConfigs[0].url
    def repository_name = repository_url.replace("https://github.com/", "").replace(".git", "")
    echo "Going to run curl command"
    withCredentials([string(credentialsId: 'enmasse-ci-github-token', variable: 'GITHUB_TOKEN')]) {
        sh(script: "curl -v -H \"Authorization: token ${GITHUB_TOKEN}\" -X POST -H \"Content-type: application/json\" -d \"@${file}\" \"https://api.github.com/repos/${repository_name}/issues/${ghprbPullId}/comments\" > out.log 2> out.err")
        def output = readFile("out.log").trim()
        def output_err = readFile("out.err").trim()
        echo "curl output=$output output_err=$output_err"
    }
}

def postAction(String artifactDir) {
    sh "sudo unlink ./go/src/github.com/enmasseproject/enmasse || true"
    storeArtifacts(artifactDir)
    makeLinePlot()
    makeStackedPlot()
    junit(testResults: '**/target/**/TEST-*.xml', allowEmptyResults: true)
    archiveArtifacts(artifacts: '**/target/**/TEST-*.xml', onlyIfSuccessful: false)
    archiveArtifacts(artifacts: 'templates/build/**', onlyIfSuccessful: false)
    sh(script: "sudo ./systemtests/scripts/wait_until_file_close.sh ${artifactDir}")
    archiveArtifacts(artifacts: "${artifactDir}/**", onlyIfSuccessful: false)
}

def installEnmasse(String tag, Boolean skipDependencies, Boolean upgrade, Boolean generateTemplates, Boolean installIoT = false) {
    if (generateTemplates) {
        sh(script: "make -C templates clean")
        sh(script: 'make templates || true')
    }
    sh(script: "DEPLOY_IOT=${installIoT} ./systemtests/scripts/deploy_enmasse.sh false 'templates/build/enmasse-${tag}' ${skipDependencies} ${upgrade}")
}

def sendMail(address, jobName, buildUrl) {
    mail(to: "${address}", subject: "EnMasse build of job ${jobName} has failed", body: "See ${buildUrl}")
}

def loginOCUser(boolean setupClusterUser) {
    sh(script: "./systemtests/scripts/login_cluster_user.sh ${setupClusterUser}")
}

def installDependencies() {
    sh(script: "ansible-playbook systemtests/ansible/playbooks/systemtests-dependencies.yml")
}

def copyTemplatesAndSetRegistries(String tag, String onlineRegistries, String brokerRouterRegistries) {
    sh(script: "mkdir -p templates/build/enmasse-${tag}")
    sh(script: "cp -r ../amq-online-images/templates/* templates/build/enmasse-${tag}")
    sh(script: "./release-scripts/replace-image-pull-spec-registry.sh ${onlineRegistries} amq7/amq-online-1 templates/build/enmasse-${tag}")
    sh(script: "./release-scripts/replace-image-pull-spec-registry.sh ${onlineRegistries} amq7-tech-preview/amq-online-1 templates/build/enmasse-${tag}")
    sh(script: "./release-scripts/replace-image-pull-spec-registry.sh ${brokerRouterRegistries} amq7/amq-interconnect templates/build/enmasse-${tag}")
    sh(script: "./release-scripts/replace-image-pull-spec-registry.sh ${brokerRouterRegistries} amq-broker-7 templates/build/enmasse-${tag}")
}

def copyStartTemplatesAndSetRegistries(String upgradeFromBranch) {
    def bundleSuffix = "start"
    if (upgradeFromBranch == "amq-online-10") {
        bundleSuffix = "1.0"
    }
    sh(script: "mkdir -p templates/build/enmasse-${bundleSuffix}")
    sh(script: "cp -r ../amq-online-images/templates/* templates/build/enmasse-${bundleSuffix}")
    sh(script: "find templates/build/enmasse-${bundleSuffix}/install -type f -print0 | xargs -0 sed -i 's@ amq7\\/@ registry.redhat.io\\/amq7\\/@g'")
    sh(script: "find templates/build/enmasse-${bundleSuffix}/install -type f -print0 | xargs -0 sed -i 's@ redhat-sso-7\\/@ registry.redhat.io\\/redhat-sso-7\\/@g'")
    sh(script: "find templates/build/enmasse-${bundleSuffix}/install -type f -print0 | xargs -0 sed -i 's@ amq-broker-7\\/@ registry.redhat.io\\/amq-broker-7\\/@g'")
    return bundleSuffix
}

def addTestNodeToOcpDns(String dnsIp, String installOcpBuildNumber) {
    if (dnsIp != '') {
        sh(script: "sudo sed -i \"1s@^@nameserver ${dnsIp} @\" /etc/resolv.conf")
    } else {
        step([$class     : 'CopyArtifact',
              projectName: "install-openshift",
              filter     : "**.txt",
              selector   : specific("${installOcpBuildNumber}"),
              target     : "${env.WORKSPACE}"])
        sh(script: "sudo sed -i \"1s@^@nameserver `cat ${WORKSPACE}/dns-ip.txt` @\" /etc/resolv.conf")
    }
}

def uninstallEnmasse(String tag, String namespace) {
    sh(script: "ansible-playbook templates/build/enmasse-${tag}/ansible/playbooks/openshift/uninstall.yml -i systemtests/ansible/inventory/systemtests.ocp4.inventory -e namespace=${namespace}")
}

def setUpOcp4() {
    sh(script: "./systemtests/scripts/set_up_ocp4.sh")
}

def deleteImages(String registry) {
    sh(script: "./systemtests/scripts/oc_delete_images.sh ${registry} amq-online-images")
}

def installOcp3(String jobName, String nodeSuffix, String containerRegistry) {
    try {
        def buildValue = build job: "${jobName}", wait: true, parameters: [
                [$class: 'StringParameterValue', name: 'SUFFIX', value: "${nodeSuffix}"],
                [$class: 'StringParameterValue', name: 'CONTAINER_REGISTRY', value: "${containerRegistry}"],
        ]
        env.OC_BUILD_NUMBER = "${buildValue.number}"
        echo env.OC_BUILD_NUMBER
    } catch(error) {
        env.OC_BUILD_NUMBER = "${error}".split("#")[1].split(" ")[0]
        echo env.OC_BUILD_NUMBER
        throw new Exception("Install openshift failed")
    }
}

return this
