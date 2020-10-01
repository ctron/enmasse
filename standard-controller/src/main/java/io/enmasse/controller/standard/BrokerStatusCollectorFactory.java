/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.controller.standard;

public interface BrokerStatusCollectorFactory {
    BrokerStatusCollector createBrokerStatusCollector();
}