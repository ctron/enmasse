/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.systemtest.utils;

@FunctionalInterface
public interface ThrowingConsumer<T> {
    public void accept(T value) throws Exception;
}
