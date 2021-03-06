/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.listener;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;

import io.enmasse.systemtest.EnmasseInstallType;
import io.enmasse.systemtest.Environment;
import io.enmasse.systemtest.bases.ThrowableRunner;
import io.enmasse.systemtest.info.TestInfo;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.logs.GlobalLogCollector;
import io.enmasse.systemtest.manager.IsolatedResourcesManager;
import io.enmasse.systemtest.manager.SharedResourceManager;
import io.enmasse.systemtest.operator.EnmasseOperatorManager;
import io.enmasse.systemtest.platform.KubeCMDClient;
import io.enmasse.systemtest.platform.Kubernetes;
import io.enmasse.systemtest.platform.cluster.KubeClusterManager;
import io.enmasse.systemtest.utils.TestUtils;

/**
 * This class implements a variety of junit callbacks and orchestates the full lifecycle of the operator installation
 * used for the test suite
 */
public class JunitCallbackListener implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler,
        AfterEachCallback, BeforeEachCallback, BeforeAllCallback, AfterAllCallback {
    private static final Logger LOGGER = CustomLogger.getLogger();
    private static final Environment env = Environment.getInstance();
    private final Kubernetes kubernetes = Kubernetes.getInstance();
    private final TestInfo testInfo = TestInfo.getInstance();
    private final IsolatedResourcesManager isolatedResourcesManager = IsolatedResourcesManager.getInstance();
    private final SharedResourceManager sharedResourcesManager = SharedResourceManager.getInstance();
    private final EnmasseOperatorManager operatorManager = EnmasseOperatorManager.getInstance();
    private static Exception beforeAllException; //TODO remove it after upgrade to surefire plugin 3.0.0-M5

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        LOGGER.info("running - beforeAll");

        testInfo.setCurrentTestClass(context);
        KubeClusterManager.getInstance().setClassConfigurations();
        try { //TODO remove it after upgrade to surefire plugin 3.0.0-M5
            handleCallBackError("Callback before all", context, () -> {
                if (testInfo.isUpgradeTest()) {
                    if (operatorManager.isEnmasseBundleDeployed()) {
                        operatorManager.deleteEnmasseBundle();
                    }
                    LOGGER.info("Enmasse is not installed because next test is {}", context.getDisplayName());
                } else if (testInfo.isOLMTest()) {
                    LOGGER.info("Test is OLM");
                    if (operatorManager.isEnmasseOlmDeployed()) {
                        operatorManager.deleteEnmasseOlm();
                    }
                    if (operatorManager.isEnmasseBundleDeployed()) {
                        operatorManager.deleteEnmasseBundle();
                    }
                    operatorManager.installEnmasseOlm(testInfo.getOLMInstallationType());
                } else if (env.installType() == EnmasseInstallType.OLM) {
                    if (!operatorManager.isEnmasseOlmDeployed()) {
                        operatorManager.installEnmasseOlm();
                    }
                    if (!operatorManager.areExamplesApplied()) {
                        operatorManager.installExamplesBundleOlm();
                        operatorManager.waitUntilOperatorReadyOlm();
                    }
                } else {
                    if (!operatorManager.isEnmasseBundleDeployed()) {
                        operatorManager.installEnmasseBundle();
                    }
                }
            });
        } catch (Exception ex) {
            beforeAllException = ex; //TODO remove it after upgrade to surefire plugin 3.0.0-M5
            operatorManager.deleteEnmasseOlm();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        LOGGER.info("running - afterAll");

        beforeAllException = null; //TODO remove it after upgrade to surefire plugin 3.0.0-M5
        handleCallBackError("Callback after all", extensionContext, () -> {
            if (!env.skipCleanup()) {
                KubeClusterManager.getInstance().restoreClassConfigurations();
            }
            if (env.skipCleanup() || env.skipUninstall()) {
                LOGGER.info("Skip cleanup/uninstall is set, enmasse operator won't be deleted");
            } else if (testInfo.isOLMTest()) {
                LOGGER.info("Test is OLM");
                if (operatorManager.isEnmasseOlmDeployed()) {
                    operatorManager.deleteEnmasseOlm();
                }
            } else if (env.installType() == EnmasseInstallType.BUNDLE) {
                if (operatorManager.isEnmasseOlmDeployed()) {
                    operatorManager.deleteEnmasseOlm();
                }
            }
        });
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        testInfo.setCurrentTest(context);
        KubeClusterManager.getInstance().setMethodConfigurations();
        logPodsInInfraNamespace();
        if (beforeAllException != null) {
            throw beforeAllException;
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        handleCallBackError("Callback after each", extensionContext, () -> {
            LOGGER.info("Teardown section: ");
            KubeClusterManager.getInstance().restoreMethodConfigurations();
            if (testInfo.isTestShared()) {
                tearDownSharedResources();
            } else {
                tearDownCommonResources();
            }
        });
    }

    private void tearDownCommonResources() throws Exception {
        LOGGER.info("Admin resource manager teardown");
        isolatedResourcesManager.tearDown(testInfo.getActualTest());
        isolatedResourcesManager.unsetReuseAddressSpace();
        isolatedResourcesManager.deleteAddressspacesFromList();
    }

    private void tearDownSharedResources() throws Exception {
        if (testInfo.isAddressSpaceDeleteable() || testInfo.getActualTest().getExecutionException().isPresent()) {
            LOGGER.info("Teardown shared!");
            sharedResourcesManager.tearDown(testInfo.getActualTest());
        } else if (sharedResourcesManager.getSharedAddressSpace() != null) {
            sharedResourcesManager.tearDownShared();
        }
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        saveKubernetesState("Test execution", context, throwable);
    }

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        saveKubernetesState("Test before all", context, throwable);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        saveKubernetesState("Test before each", context, throwable);
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        saveKubernetesState("Test after each", context, throwable);
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        saveKubernetesState("Test after all", context, throwable);
    }

    private void handleCallBackError(String description, ExtensionContext context, ThrowableRunner runnable) throws Exception {
        try {
            runnable.run();
        } catch (Exception ex) {
            try {
                saveKubernetesState(description, context, ex);
            } catch (Throwable ignored) {
            }
            LOGGER.error("Exception captured in Junit callback", ex);
            throw ex;
        }
    }

    private void saveKubernetesState(String description, ExtensionContext extensionContext, Throwable throwable) throws Throwable {
        LOGGER.error("Test failed at {}", description);
        logPodsInInfraNamespace();
        if (env.isSkipSaveState()) {
            throw throwable;
        }
        GlobalLogCollector.saveInfraState(TestUtils.getFailedTestLogsPath(extensionContext));
        throw throwable;
    }

    private void logPodsInInfraNamespace() {
        KubeCMDClient.runOnCluster("get", "pods", "-n", kubernetes.getInfraNamespace(), "-o", "wide");
    }

}
