/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.device;

import static io.enmasse.iot.registry.infinispan.device.data.DeviceKey.deviceKey;
import static io.enmasse.iot.service.base.utils.MoreFutures.completeHandler;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.infinispan.client.hotrod.RemoteCache;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.enmasse.iot.registry.infinispan.cache.AdapterCredentialsCacheProvider;
import io.enmasse.iot.registry.infinispan.cache.DeviceManagementCacheProvider;
import io.enmasse.iot.registry.infinispan.device.data.CredentialKey;
import io.enmasse.iot.registry.infinispan.device.data.DeviceInformation;
import io.enmasse.iot.registry.infinispan.device.data.DeviceKey;
import io.enmasse.iot.registry.infinispan.tenant.TenantInformationService;
import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public abstract class AbstractCredentialsManagementService implements CredentialsManagementService {

    private HonoPasswordEncoder passwordEncoder;

    // Adapter cache :
    // <(tenantId + authId + type), (credential + deviceId)>
    protected final RemoteCache<CredentialKey, String> adapterCache;

    // Management cache
    // <(tenantId + deviceId), (device information + version + credentials)>
    protected final RemoteCache<DeviceKey, DeviceInformation> managementCache;

    @Autowired
    protected TenantInformationService tenantInformationService;

    private final ExecutorService encoderThreadPool;

    @Autowired
    public AbstractCredentialsManagementService(final DeviceManagementCacheProvider managementProvider, final AdapterCredentialsCacheProvider adapterProvider,
            final HonoPasswordEncoder passwordEncoder) {
        this(managementProvider.getDeviceManagementCache(), adapterProvider.getAdapterCredentialsCache(), passwordEncoder, Runtime.getRuntime().availableProcessors());
    }

    AbstractCredentialsManagementService(final RemoteCache<DeviceKey, DeviceInformation> managementCache, final RemoteCache<CredentialKey, String> adapterCache,
            final HonoPasswordEncoder passwordEncoder, int hashThreadPoolSize) {
        this.encoderThreadPool = Executors.newFixedThreadPool(hashThreadPoolSize, new ThreadFactoryBuilder().setNameFormat("pwd-hash-thread-%d").build());
        this.adapterCache = adapterCache;
        this.managementCache = managementCache;
        this.passwordEncoder = passwordEncoder;
    }

    @PreDestroy
    public void close() {
        this.encoderThreadPool.shutdown();
    }

    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    @Override
    public void set(final String tenantId, final String deviceId, final Optional<String> resourceVersion, final List<CommonCredential> credentials, final Span span,
            final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {
        completeHandler(() -> processSet(tenantId, deviceId, resourceVersion, credentials, span), resultHandler);
    }

    protected CompletableFuture<OperationResult<Void>> processSet(final String tenantId, final String deviceId, final Optional<String> resourceVersion,
            final List<CommonCredential> credentials, final Span span) {

        return verifyAndEncodePasswords(credentials)
                .thenCompose(encodedCredentials -> {
                    return this.tenantInformationService
                            .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                            .thenCompose(tenantHandle -> processSet(deviceKey(tenantHandle, deviceId), resourceVersion, encodedCredentials, span));
                })
                .exceptionally(e -> {
                    if (Throwables.getRootCause(e) instanceof IllegalStateException) {
                        // An illegal state exception is actually a bad request
                        return OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST);
                    } else if (e instanceof RuntimeException) {
                        // don't pollute the cause chain
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                });

    }

    protected abstract CompletableFuture<OperationResult<Void>> processSet(DeviceKey key, Optional<String> resourceVersion, List<CommonCredential> credentials,
            Span span);

    @Override
    public void get(final String tenantId, final String deviceId, final Span span, final Handler<AsyncResult<OperationResult<List<CommonCredential>>>> resultHandler) {
        completeHandler(() -> processGet(tenantId, deviceId, span), resultHandler);
    }

    protected CompletableFuture<OperationResult<List<CommonCredential>>> processGet(final String tenantId, final String deviceId, final Span span) {

        return this.tenantInformationService
                .tenantExists(tenantId, HTTP_NOT_FOUND, span)
                .thenCompose(tenantHandle -> processGet(deviceKey(tenantHandle, deviceId), span));

    }

    protected abstract CompletableFuture<OperationResult<List<CommonCredential>>> processGet(DeviceKey key, Span span);

    private CompletableFuture<List<CommonCredential>> verifyAndEncodePasswords(final List<CommonCredential> credentials) {
        return CompletableFuture.supplyAsync(() -> {
            for (final CommonCredential credential : credentials) {
                checkCredential(credential);
            }
            return credentials;
        }, this.encoderThreadPool);
    }


    /**
     * Validate a secret and hash the password if necessary.
     *
     * @param credential The secret to validate.
     * @throws IllegalStateException if the secret is not valid.
     */
    private void checkCredential(final CommonCredential credential) {
        credential.checkValidity();
        if (credential instanceof PasswordCredential) {
            for (final PasswordSecret passwordSecret : ((PasswordCredential) credential).getSecrets()) {
                passwordSecret.encode(this.passwordEncoder);
                passwordSecret.checkValidity();
            }
        }
    }
}
