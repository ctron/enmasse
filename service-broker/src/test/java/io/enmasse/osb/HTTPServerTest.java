/*
 * Copyright 2016-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.osb;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AddressSpaceBuilder;
import io.enmasse.address.model.AddressSpaceStatus;
import io.enmasse.address.model.EndpointSpec;
import io.enmasse.address.model.EndpointSpecBuilder;
import io.enmasse.api.auth.AuthApi;
import io.enmasse.api.auth.SubjectAccessReview;
import io.enmasse.api.auth.TokenReview;
import io.enmasse.k8s.api.TestAddressSpaceApi;
import io.enmasse.osb.api.provision.ConsoleProxy;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class HTTPServerTest {

    private Vertx vertx;
    private TestAddressSpaceApi instanceApi;
    private AddressSpace addressSpace;
    private HTTPServer httpServer;

    @BeforeEach
    public void setup(VertxTestContext context) {
        vertx = Vertx.vertx();
        instanceApi = new TestAddressSpaceApi();
        String addressSpaceName = "myinstance";
        addressSpace = createAddressSpace(addressSpaceName);
        instanceApi.createAddressSpace(addressSpace);

        AuthApi authApi = mock(AuthApi.class);
        when(authApi.getNamespace()).thenReturn("controller");
        when(authApi.performTokenReview(eq("mytoken"))).thenReturn(new TokenReview("foo", "myid", true));
        when(authApi.performSubjectAccessReviewResource(eq("foo"), any(), any(), any(), anyString())).thenReturn(new SubjectAccessReview("foo", true));
        when(authApi.performSubjectAccessReviewResource(eq("foo"), any(), any(), any(), anyString())).thenReturn(new SubjectAccessReview("foo", true));
        httpServer = new HTTPServer(instanceApi, new TestSchemaProvider(), authApi, null, false, null, 0, new ConsoleProxy() {
            @Override
            public String getConsoleUrl(AddressSpace addressSpace) {
                return "http://localhost/console/" + addressSpaceName;
            }
        });
        vertx.deployVerticle(httpServer, context.succeeding(id -> context.completeNow()));
    }

    @AfterEach
    public void teardown(VertxTestContext context) {
        vertx.close(context.succeeding(id -> context.completeNow()));
    }

    private AddressSpace createAddressSpace(String name) {
        return new AddressSpaceBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .withNamespace(name)
                        .build())

                .withNewSpec()
                .withType("mytype")
                .withPlan("myplan")

                .addToEndpoints(new EndpointSpecBuilder()
                        .withName("foo")
                        .withService("messaging")
                        .build())
                .endSpec()

                .withNewStatus(false)

                .build();
    }

    @Test
    public void testOpenServiceBrokerAPI(VertxTestContext context) throws InterruptedException {
        HttpClientOptions options = new HttpClientOptions();
        HttpClient client = vertx.createHttpClient(options);
        try {
            HttpClientRequest request = client.get(httpServer.getActualPort(), "localhost", "/osbapi/v2/catalog", response -> {
                response.bodyHandler(buffer -> {
                    JsonObject data = buffer.toJsonObject();
                    context.verify(() -> assertTrue(data.containsKey("services")));
                    context.completeNow();
                });
            });
            putAuthzToken(request);
            request.end();
            context.awaitCompletion(60, TimeUnit.SECONDS);
        } finally {
            client.close();
        }
    }

    private static HttpClientRequest putAuthzToken(HttpClientRequest request) {
        request.putHeader("Authorization", "Bearer mytoken");
        return request;
    }
}
