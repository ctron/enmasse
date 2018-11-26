/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.hono.qdr.configurator;

public class Application {
    public static void main(final String[] args) {
        try (Configurator cfg = new Configurator()) {
            cfg.run();
        }
    }
}
