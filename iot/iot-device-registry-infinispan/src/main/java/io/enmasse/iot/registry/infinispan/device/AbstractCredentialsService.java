/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.device;

import static io.enmasse.iot.registry.infinispan.device.data.CredentialKey.credentialKey;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.util.CredentialsResult;
import org.infinispan.client.hotrod.RemoteCache;
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
import io.vertx.core.json.JsonObject;

public abstract class AbstractCredentialsService extends AbstractInfinispanService implements CredentialsService {

    // Adapter cache :
    // <( tenantId + authId + type), (adapter credentials)>
    protected final RemoteCache<CredentialKey, String> adapterCache;

    // Management cache
    // <(TenantId+DeviceId), (Device information + version + credentials)>
    protected final RemoteCache<DeviceKey, DeviceInformation> managementCache;

    @Autowired
    protected TenantInformationService tenantInformationService;

    @Autowired
    public AbstractCredentialsService(final DeviceManagementCacheProvider managementProvider, final AdapterCredentialsCacheProvider adapterProvider) {
        super("CredentialsService");
        this.adapterCache = adapterProvider.getAdapterCredentialsCache();
        this.managementCache = managementProvider.getDeviceManagementCache();
    }

    public void setTenantInformationService(TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        completeHandler(() -> processGet(tenantId, type, authId, span), resultHandler);
    }

    @Override
    public void get(String tenantId, String type, String authId, JsonObject clientContext, Span span, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        get(tenantId, type, authId, span, resultHandler);
    }

    protected CompletableFuture<CredentialsResult<JsonObject>> processGet(final String tenantId, final String type, final String authId, final Span span) {

        final CredentialKey key = credentialKey(tenantId, authId, type);

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span )
                .thenCompose(x -> processGet(key, span));

    }

    protected abstract CompletableFuture<CredentialsResult<JsonObject>> processGet(CredentialKey key, Span span);

}
