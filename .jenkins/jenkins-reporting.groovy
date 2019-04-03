def pushJunitArtifacts(String ARTIFACTS_URL, String PRODUCT_VERSION, String REPORT_NAME, String BUILD_TAG) {
    dir('enmasse') {
        dir('systemtests/target') {
            sh(script: "zip -r ${REPORT_NAME}_${BUILD_TAG}.zip ${REPORT_NAME}")
        }
        sh(script: "curl -u anonymous: -X PUT \"${ARTIFACTS_URL}/enmasse-test-results/${PRODUCT_VERSION}/\" -T systemtests/target/${REPORT_NAME}_${BUILD_TAG}.zip")
        sh(script: "rm -f systemtests/target/${REPORT_NAME}_${BUILD_TAG}.zip")
    }
}

def triggerReporting(String REPORT_FILE_URL, String PARAMS, String PRODUCT_VERSION, String PRODUCT_SUBVERSION, String OC_VERSION) {
    dir("dtests-config") {
        sh(script: "sed -i '/ZIP_FILE_URL/!b;n;c\\                \"value\": \"$REPORT_FILE_URL\"' ${PARAMS}")
        sh(script: "sed -i '/PRODUCT_VERSION/!b;n;c\\                \"value\": \"${PRODUCT_VERSION}/${PRODUCT_SUBVERSION}/${OC_VERSION}\"' ${PARAMS}")
        sh(script: "curl -X POST http://primary-ci.messaging.lab.eng.brq.redhat.com:8080/view/Reports/job/external-polarion-reporter-executor/build?token=run --data-urlencode json=\"\$(<$PARAMS)\"")
    }
}

return this