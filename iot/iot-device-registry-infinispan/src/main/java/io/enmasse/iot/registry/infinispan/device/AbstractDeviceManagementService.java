/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.device;

import org.springframework.beans.factory.annotation.Autowired;

import io.enmasse.iot.registry.infinispan.cache.AdapterCredentialsCacheProvider;
import io.enmasse.iot.registry.infinispan.cache.DeviceManagementCacheProvider;
import io.enmasse.iot.registry.infinispan.device.data.CredentialKey;
import io.enmasse.iot.registry.infinispan.device.data.DeviceInformation;
import io.enmasse.iot.registry.infinispan.device.data.DeviceKey;
import io.enmasse.iot.registry.infinispan.service.AbstractInfinispanService;
import io.enmasse.iot.registry.infinispan.tenant.TenantInformationService;
import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import static io.enmasse.iot.registry.infinispan.device.data.DeviceKey.deviceKey;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.infinispan.client.hotrod.RemoteCache;

public abstract class AbstractDeviceManagementService extends AbstractInfinispanService implements DeviceManagementService {

    // Adapter cache :
    // <( tenantId + authId + type), (credential + deviceId + sync-flag + registration data version)>
    protected final RemoteCache<CredentialKey, String> adapterCache;

    // Management cache
    // <(TenantId+DeviceId), (Device information + version + credentials)>
    protected final RemoteCache<DeviceKey, DeviceInformation> managementCache;

    private final Supplier<String> deviceIdGenerator = () -> UUID.randomUUID().toString();

    @Autowired
    protected TenantInformationService tenantInformationService;

    @Autowired
    public AbstractDeviceManagementService(final DeviceManagementCacheProvider managementProvider, final AdapterCredentialsCacheProvider adapterProvider) {
        super("DeviceManagementService");
        this.adapterCache = adapterProvider.getAdapterCredentialsCache();
        this.managementCache = managementProvider.getDeviceManagementCache();
    }

    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    protected abstract CompletableFuture<OperationResult<Id>> processCreateDevice(DeviceKey key, Device device, Span span);

    protected abstract CompletableFuture<OperationResult<Device>> processReadDevice(DeviceKey key, Span span);

    protected abstract CompletableFuture<OperationResult<Id>> processUpdateDevice(DeviceKey key, Device device, Optional<String> resourceVersion, Span span);

    protected abstract CompletableFuture<Result<Void>> processDeleteDevice(DeviceKey key, Optional<String> resourceVersion, Span span);

    @Override
    public void createDevice(String tenantId, Optional<String> deviceId, Device device, Span span, Handler<AsyncResult<OperationResult<Id>>> resultHandler) {
        completeHandler(() -> processCreateDevice(tenantId, deviceId, device, span), resultHandler);
    }

    protected CompletableFuture<OperationResult<Id>> processCreateDevice(final String tenantId, final Optional<String> optionalDeviceId, final Device device, final Span span) {

        final String deviceId = optionalDeviceId.orElseGet(this.deviceIdGenerator);
        final DeviceKey key = deviceKey(tenantId, deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                .thenCompose(x -> processCreateDevice(key, device, span));

    }

    @Override
    public void readDevice(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<OperationResult<Device>>> resultHandler) {
        completeHandler(() -> processReadDevice(tenantId, deviceId, span), resultHandler);
    }

    protected CompletableFuture<OperationResult<Device>> processReadDevice(String tenantId, String deviceId, Span span) {

        final DeviceKey key = deviceKey(tenantId, deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                .thenCompose(x -> processReadDevice(key, span));

    }

    @Override
    public void updateDevice(String tenantId, String deviceId, Device device, Optional<String> resourceVersion, Span span,
            Handler<AsyncResult<OperationResult<Id>>> resultHandler) {
        completeHandler(() -> processUpdateDevice(tenantId, deviceId, device, resourceVersion, span), resultHandler);
    }

    protected CompletableFuture<OperationResult<Id>> processUpdateDevice(String tenantId, String deviceId, Device device, Optional<String> resourceVersion, Span span) {

        final DeviceKey key = deviceKey(tenantId, deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                .thenCompose(x -> processUpdateDevice(key, device, resourceVersion, span));

    }

    @Override
    public void deleteDevice(String tenantId, String deviceId, Optional<String> resourceVersion, Span span, Handler<AsyncResult<Result<Void>>> resultHandler) {
        completeHandler(() -> processDeleteDevice(tenantId, deviceId, resourceVersion, span), resultHandler);
    }

    protected CompletableFuture<Result<Void>> processDeleteDevice(String tenantId, String deviceId, Optional<String> resourceVersion, Span span) {

        final DeviceKey key = deviceKey(tenantId, deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                .thenCompose(x -> processDeleteDevice(key, resourceVersion, span));

    }

}
