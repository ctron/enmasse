/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.tenant.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.HttpURLConnection;
import java.util.HashMap;

import org.eclipse.hono.util.TenantResult;
import org.junit.jupiter.api.Test;

import io.enmasse.iot.model.v1.IoTProject;
import io.enmasse.iot.model.v1.IoTProjectBuilder;
import io.vertx.core.json.JsonObject;

public class AbstractTenantServiceTest {

    private final AbstractTenantService service;

    public AbstractTenantServiceTest() {
        this.service = new AbstractTenantService() {};
        this.service.configuration = new TenantServiceConfigProperties();
    }

    @Test
    public void testConvertForHonoNoStatus() {
        final IoTProject project = new IoTProjectBuilder().build();
        var result = service.convertToHono("tenant", project);

        assertNotFound(result);
    }

    @Test
    public void testConvertForHonoNoAcceptedStatus() {
        final IoTProject project = new IoTProjectBuilder()
                .withNewStatus()
                .endStatus()
                .build();

        var result = service.convertToHono("tenant", project);

        assertNotFound(result);
    }

    @Test
    public void testConvertForHonoNoAcceptedConfiguration() {
        final IoTProject project = new IoTProjectBuilder()
                .withNewStatus()
                .withNewAccepted()
                .endAccepted()
                .endStatus()
                .build();

        var result = service.convertToHono("tenant", project);

        assertNotFound(result);
    }

    @Test
    public void testConvertForHonoEmptyAcceptedConfiguration() {
        final IoTProject project = new IoTProjectBuilder()
                .withNewStatus()
                .withNewAccepted()
                .withConfiguration(new HashMap<>())
                .endAccepted()
                .endStatus()
                .build();

        var result = service.convertToHono("tenant", project);

        assertFound(result);
    }

    private void assertNotFound(TenantResult<JsonObject> result) {
        assertNotNull(result);
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, result.getStatus());
    }

    private void assertFound(TenantResult<JsonObject> result) {
        assertNotNull(result);
        assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
    }
}
