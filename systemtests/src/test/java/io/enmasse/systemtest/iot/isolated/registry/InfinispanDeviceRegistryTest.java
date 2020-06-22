/*
 * Copyright 2019-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.systemtest.iot.isolated.registry;

import static io.enmasse.systemtest.TestTag.ACCEPTANCE;
import static io.enmasse.systemtest.iot.DefaultDeviceRegistry.newInfinispanBased;
import static io.enmasse.systemtest.utils.IoTUtils.assertCorrectDeviceConnectionType;
import static io.enmasse.systemtest.utils.IoTUtils.assertCorrectRegistryType;

import java.net.HttpURLConnection;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.enmasse.iot.model.v1.IoTConfigBuilder;
import io.enmasse.systemtest.iot.IoTTestSession;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class InfinispanDeviceRegistryTest extends DeviceRegistryTest {

    @Override
    protected int tenantDoesNotExistCode() {
        return HttpURLConnection.HTTP_UNAUTHORIZED;
    }

    @Override
    protected IoTConfigBuilder provideIoTConfig() throws Exception {
        return IoTTestSession
                .createDefaultConfig()
                .editOrNewSpec()
                .withServices(newInfinispanBased())
                .endSpec();
    }

    @Test
    void testCorrectTypeDeployed () {
        assertCorrectDeviceConnectionType("infinispan");
        assertCorrectRegistryType("infinispan");
    }

    @Test
    @Tag(ACCEPTANCE)
    void testRegisterDevice() throws Exception {
        super.doTestRegisterDevice();
    }

    @Test
    @Tag(ACCEPTANCE)
    void testDisableDevice() throws Exception {
        super.doTestDisableDevice();
    }

    @Test
    void testDeviceCredentials() throws Exception {
        super.doTestDeviceCredentials();
    }

    @Test
    void testDeviceCredentialsPlainPassword() throws Exception {
        super.doTestDeviceCredentialsPlainPassword();
    }

    @Test
    @Disabled("Fixed in hono/pull/1565")
    void testDeviceCredentialsDoesNotContainsPasswordDetails() throws Exception {
        super.doTestDeviceCredentialsDoesNotContainsPasswordDetails();
    }

    @Test
    @Disabled("Caches expire a bit unpredictably")
    void testCacheExpiryForCredentials() throws Exception {
        super.doTestCacheExpiryForCredentials();
    }

    @Test
    void testSetExpiryForCredentials() throws Exception {
        super.doTestSetExpiryForCredentials();
    }

    @Test
    void testCreateForNonExistingTenantFails() throws Exception {
        super.doTestCreateForNonExistingTenantFails();
    }

    @Test
    void testCreateDuplicateDeviceFails() throws Exception {
        super.doCreateDuplicateDeviceFails();
    }

    @Test
    void testRegisterMultipleDevices() throws Exception {
        super.doRegisterMultipleDevices();
    }

    @Test
    void testTenantDeletionTriggersDevicesDeletion() throws Exception {
        super.doTestTenantDeletionTriggersDevicesDeletion();
    }

    @Override
    @Disabled("Not supported by Infinispan")
    public void testDeviceWithSameAuthIdOfSameTypesFails(final Vertx vertx) throws Exception {
        super.testDeviceWithSameAuthIdOfSameTypesFails(vertx);
    }
}
