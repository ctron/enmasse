/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.device;

import static io.enmasse.iot.infinispan.device.DeviceKey.deviceKey;
import static io.enmasse.iot.utils.MoreFutures.completeHandler;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.util.RegistrationResult;
import org.infinispan.client.hotrod.RemoteCache;
import org.springframework.beans.factory.annotation.Autowired;

import io.enmasse.iot.infinispan.cache.DeviceManagementCacheProvider;
import io.enmasse.iot.infinispan.device.DeviceInformation;
import io.enmasse.iot.infinispan.device.DeviceKey;
import io.enmasse.iot.registry.tenant.TenantInformationService;
import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public abstract class AbstractRegistrationService extends org.eclipse.hono.service.registration.AbstractRegistrationService {

    // Management cache
    // <(TenantId+DeviceId), (Device information + version + credentials)>
    protected final RemoteCache<DeviceKey, DeviceInformation> managementCache;

    @Autowired
    protected TenantInformationService tenantInformationService;

    @Autowired
    public AbstractRegistrationService(final DeviceManagementCacheProvider provider) {
        this.managementCache = provider.getOrCreateDeviceManagementCache();
    }

    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    @Override
    protected void getDevice(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        completeHandler(() -> processGetDevice(tenantId, deviceId, span), resultHandler);
    }

    protected CompletableFuture<RegistrationResult> processGetDevice(final String tenantName, final String deviceId, final Span span) {

        return this.tenantInformationService
                .tenantExists(tenantName, HTTP_NOT_FOUND, span)
                .thenCompose(tenantId -> processGetDevice(deviceKey(tenantId, deviceId), span));

    }

    protected abstract CompletableFuture<RegistrationResult> processGetDevice(DeviceKey key, Span span);

}
