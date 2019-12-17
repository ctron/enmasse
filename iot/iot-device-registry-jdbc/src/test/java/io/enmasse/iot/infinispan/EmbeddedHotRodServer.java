/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.infinispan;

import java.net.ServerSocket;
import java.util.UUID;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.server.core.admin.embeddedserver.EmbeddedServerAdminOperationHandler;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfiguration;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.enmasse.iot.infinispan.cache.DeviceManagementCacheProvider;
import io.enmasse.iot.infinispan.cache.DeviceConnectionCacheProvider;
import io.enmasse.iot.infinispan.config.InfinispanProperties;
import io.enmasse.iot.infinispan.devcon.DeviceConnectionKey;

/**
 * This is heavily inspired from Tristan Tarrant's SimpleEmbeddedHotRodServer.
 * Mimics a remote server using an embedded cache
 *
 * https://github.com/tristantarrant/infinispan-playground-embedded-hotrod/blob/master/src/main/java/net/dataforte/infinispan/playground/embeddedhotrod/SimpleEmbeddedHotRodServer.java
 */
public class EmbeddedHotRodServer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedHotRodServer.class);

    private final HotRodServer server;
    private final DefaultCacheManager defaultCacheManager;

    private DeviceManagementCacheProvider deviceProvider;
    private DeviceConnectionCacheProvider stateProvider;

    public EmbeddedHotRodServer() throws Exception {

        GlobalConfiguration globalConfig = new GlobalConfigurationBuilder()
                .defaultCacheName("default")
                .transport()
                .clusterName(UUID.randomUUID().toString())
                .defaultTransport()

                .build();

        var config = new org.infinispan.configuration.cache.ConfigurationBuilder()
                .build();

        this.defaultCacheManager = new DefaultCacheManager(globalConfig, config);

        /*
         * Unfortunately some parts of the hot rod server can't handle
         * the ephemeral port zero.
         */
        int port = freePort();
        log.info("Using port: {}", port);

        final HotRodServerConfiguration build = new HotRodServerConfigurationBuilder()
                .adminOperationsHandler(new EmbeddedServerAdminOperationHandler())
                .port(port)
                .defaultCacheName("default")
                .startTransport(true)
                .build();

        this.server = new HotRodServer();
        this.server.start(build, this.defaultCacheManager);

        final InfinispanProperties properties = new InfinispanProperties();
        properties.setTryCreate(true);
        properties.setPort(server.getPort());

        this.deviceProvider = new DeviceManagementCacheProvider(properties);
        this.deviceProvider.start();
        this.stateProvider = new DeviceConnectionCacheProvider(properties);
        this.stateProvider.start();
    }

    private static int freePort() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            return server.getLocalPort();
        }
    }

    public void stop() throws Exception {
        try {
            this.deviceProvider.stop();
        } finally {
            this.stateProvider.stop();
        }
    }

    public RemoteCache<DeviceConnectionKey, String> getDeviceStateCache() {
        return this.stateProvider.getDeviceStateTestCache();
    }
}
