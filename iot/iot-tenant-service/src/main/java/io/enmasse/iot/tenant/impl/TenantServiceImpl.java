/*
 * Copyright 2018-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.tenant.impl;

import static io.vertx.core.Future.failedFuture;

import java.net.HttpURLConnection;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

@Service
@Qualifier("backend")
public class TenantServiceImpl extends AbstractTenantService {

    private static final Logger logger = LoggerFactory.getLogger(TenantServiceImpl.class);

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantName, final Span span) {

        logger.debug("Get tenant - name: {}", tenantName);

        span.log(ImmutableMap.<String,Object>builder()
                .put("event", "get tenant")
                .put("tenant_id", tenantName)
                .build());

        return getProject(tenantName)

                .map(project -> project
                        .map(p -> convertToHono(tenantName, p))
                        .orElse(RESULT_NOT_FOUND));

    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {
        return failedFuture(new ServerErrorException(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

}
