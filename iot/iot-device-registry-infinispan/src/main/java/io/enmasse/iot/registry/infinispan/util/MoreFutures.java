/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.util;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public final class MoreFutures {

    private static final Logger log = LoggerFactory.getLogger(MoreFutures.class);

    private MoreFutures() {}

    public static <T> void completeHandler(final Supplier<CompletableFuture<T>> supplier, final Handler<AsyncResult<T>> handler) {

        // fail with NPE, as we have no one to report to

        Objects.requireNonNull(handler);

        if (supplier == null) {

            // report to handler that we failed

            handler.handle(Future.failedFuture(new NullPointerException("'future' to handle must not be 'null'")));

        }

        // create the future

        final CompletableFuture<T> future;
        try {
            future = supplier.get();
        } catch (final Exception e) {
            log.debug("Failed to prepare future", e);
            handler.handle(Future.failedFuture(e));
            return;
        }

        // test for null

        if ( future == null ) {
            handler.handle(Future.failedFuture("Supplier failed to provide an operation future"));
            return;
        }

        // hook up completion

        future.whenComplete((result, error) -> {
            log.debug("Result - {}", result, error);
            if (error == null) {
                handler.handle(Future.succeededFuture(result));
            } else {
                log.debug("Future failed", error);
                handler.handle(Future.failedFuture(error));
            }
        });

    }

    public static CompletableFuture<Void> allOf(final List<CompletableFuture<?>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

}
