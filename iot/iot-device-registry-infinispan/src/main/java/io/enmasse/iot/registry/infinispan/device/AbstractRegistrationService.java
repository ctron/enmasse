/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.device;

import static io.enmasse.iot.registry.infinispan.device.data.DeviceKey.deviceKey;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.infinispan.client.hotrod.RemoteCache;
import org.springframework.beans.factory.annotation.Autowired;

import io.enmasse.iot.registry.infinispan.cache.DeviceManagementCacheProvider;
import io.enmasse.iot.registry.infinispan.device.data.DeviceInformation;
import io.enmasse.iot.registry.infinispan.device.data.DeviceKey;
import io.enmasse.iot.registry.infinispan.service.AbstractInfinispanService;
import io.enmasse.iot.registry.infinispan.tenant.TenantInformationService;
import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public abstract class AbstractRegistrationService extends AbstractInfinispanService implements RegistrationService {

    private org.eclipse.hono.service.registration.AbstractRegistrationService serviceAdapter = new org.eclipse.hono.service.registration.AbstractRegistrationService() {

        @Override
        protected void getDevice(String tenantId, String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler) {
            AbstractRegistrationService.this.getDevice(tenantId, deviceId, resultHandler);
        }

    };

    // Management cache
    // <(TenantId+DeviceId), (Device information + version + credentials)>
    protected final RemoteCache<DeviceKey, DeviceInformation> managementCache;

    @Autowired
    protected TenantInformationService tenantInformationService;

    @Autowired
    public AbstractRegistrationService(final DeviceManagementCacheProvider provider) {
        super("RegistrationService");
        this.managementCache = provider.getDeviceManagementCache();
    }

    public void setTenantInformationService(TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        this.serviceAdapter.assertRegistration(tenantId, deviceId, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        this.serviceAdapter.assertRegistration(tenantId, deviceId, gatewayId, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        this.serviceAdapter.assertRegistration(tenantId, deviceId, span, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId, final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        this.serviceAdapter.assertRegistration(tenantId, deviceId, gatewayId, span, resultHandler);
    }

    protected void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        completeHandler(() -> processGetDevice(tenantId, deviceId, NoopSpan.INSTANCE), resultHandler);
    }

    protected CompletableFuture<RegistrationResult> processGetDevice(final String tenantId, final String deviceId, final Span span) {

        final DeviceKey key = deviceKey(tenantId, deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                .thenCompose(x -> processGetDevice(key, span));

    }

    protected abstract CompletableFuture<RegistrationResult> processGetDevice(DeviceKey key, Span span);

}
