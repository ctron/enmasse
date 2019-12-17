/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.autowire;

import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.EventBusCredentialsAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A default event bus based service implementation of the {@link CredentialsService}.
 * <p>
 * This wires up the actual service instance with the mapping to the event bus implementation. It is intended to be used
 * in a Spring Boot environment.
 */
@Component
public final class AutowiredCredentialsAdapter extends EventBusCredentialsAdapter {

    private CredentialsService service;

    @Autowired
    public void setService(final CredentialsService service) {
        this.service = service;
    }

    @Override
    protected CredentialsService getService() {
        return this.service;
    }

}
