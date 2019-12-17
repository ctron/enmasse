/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.device.impl;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import java.util.concurrent.CompletableFuture;

import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.enmasse.iot.infinispan.cache.DeviceManagementCacheProvider;
import io.enmasse.iot.registry.infinispan.device.AbstractRegistrationService;
import io.enmasse.iot.infinispan.device.DeviceKey;
import io.opentracing.Span;
import io.vertx.core.json.JsonObject;

@Component
public class RegistrationServiceImpl extends AbstractRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    public RegistrationServiceImpl(final DeviceManagementCacheProvider provider) {
        super(provider);
    }

    @Override
    protected CompletableFuture<RegistrationResult> processGetDevice(final DeviceKey key, final Span span) {

        return this.managementCache

                .getWithMetadataAsync(key)
                .thenApply(result -> {

                    if (result == null) {
                        return RegistrationResult.from(HTTP_NOT_FOUND);
                    }

                    log.debug("Found device: {}", result);

                    return RegistrationResult.from(HTTP_OK, convertTo(result.getValue().getRegistrationInformationAsJson()));
                });

    }

    private static JsonObject convertTo(final JsonObject deviceInfo) {
        return new JsonObject()
                .put(RegistrationConstants.FIELD_DATA, deviceInfo);
    }

}
