/*
 * Copyright 2018-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.tenant.impl;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import java.net.HttpURLConnection;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantResult;
import org.springframework.beans.factory.annotation.Autowired;

import io.enmasse.iot.model.v1.IoTProject;
import io.enmasse.iot.service.base.AbstractProjectBasedService;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public abstract class AbstractTenantService extends AbstractProjectBasedService implements TenantService {

    protected static final TenantResult<JsonObject> RESULT_NOT_FOUND = TenantResult.from(HTTP_NOT_FOUND);

    protected TenantServiceConfigProperties configuration;

    @Autowired
    public void setConfig(final TenantServiceConfigProperties configuration) {
        this.configuration = configuration;
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId) {
        return get(tenantId, NoopSpan.INSTANCE);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn) {
        return get(subjectDn, NoopSpan.INSTANCE);
    }

    protected TenantResult<JsonObject> convertToHono(final String tenantName, final IoTProject project) {

        if (project.getStatus() == null || project.getStatus().getAccepted() == null) {
            // controller has not yet processed the configuration ... handle as "not found"
            return RESULT_NOT_FOUND;
        }

        if (project.getStatus().getAccepted().getConfiguration() == null) {
            // controller processed the configuration, but rejected it ... handle as "not found"
            return RESULT_NOT_FOUND;
        }

        final JsonObject payload = JsonObject.mapFrom(project.getStatus().getAccepted().getConfiguration());

        // always add (and override) the tenant id

        payload.put(Constants.JSON_FIELD_TENANT_ID, tenantName);

        // return result

        return TenantResult.from(
                HttpURLConnection.HTTP_OK,
                payload,
                CacheDirective.maxAgeDirective(this.configuration.getCacheTimeToLive().getSeconds()));

    }

}
