/*
 * Copyright 2019-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.iot.isolated;

import static io.enmasse.systemtest.TestTag.ACCEPTANCE;
import static io.enmasse.systemtest.iot.DefaultDeviceRegistry.newDefaultInstance;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.iot.model.v1.IoTConfig;
import io.enmasse.iot.model.v1.IoTProject;
import io.enmasse.systemtest.Endpoint;
import io.enmasse.systemtest.TestTag;
import io.enmasse.systemtest.amqp.AmqpClient;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.bases.iot.ITestIoTIsolated;
import io.enmasse.systemtest.iot.CredentialsRegistryClient;
import io.enmasse.systemtest.iot.DeviceRegistryClient;
import io.enmasse.systemtest.iot.HttpAdapterClient;
import io.enmasse.systemtest.iot.IoTProjectTestContext;
import io.enmasse.systemtest.iot.IoTTestSession;
import io.enmasse.systemtest.iot.MessageSendTester;
import io.enmasse.systemtest.iot.MessageSendTester.ConsumerFactory;
import io.enmasse.systemtest.logs.CustomLogger;
import io.enmasse.systemtest.mqtt.MqttClientFactory;
import io.enmasse.systemtest.time.TimeoutBudget;
import io.enmasse.systemtest.time.WaitPhase;
import io.enmasse.systemtest.utils.IoTUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.enmasse.user.model.v1.Operation;
import io.enmasse.user.model.v1.User;
import io.enmasse.user.model.v1.UserAuthenticationType;
import io.enmasse.user.model.v1.UserAuthorizationBuilder;
import io.enmasse.user.model.v1.UserBuilder;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@Tag(TestTag.SMOKE)
@ExtendWith(VertxExtension.class)
class MultipleProjectsTest extends TestBase implements ITestIoTIsolated {
    private static Logger log = CustomLogger.getLogger();
    private DeviceRegistryClient registryClient;
    private CredentialsRegistryClient credentialsClient;

    private int numberOfProjects = 2;
    private List<IoTProjectTestContext> projects = new ArrayList<>();

    @BeforeEach
    void initEnv(final Vertx vertx) throws Exception {
        IoTConfig iotConfig = IoTTestSession.createDefaultConfig()
                .editOrNewSpec().withServices(newDefaultInstance()).endSpec()
                .build();
        isolatedIoTManager.createIoTConfig(iotConfig);

        Endpoint deviceRegistryEndpoint = IoTUtils.getDeviceRegistryManagementEndpoint();
        registryClient = new DeviceRegistryClient(vertx, deviceRegistryEndpoint);
        credentialsClient = new CredentialsRegistryClient(vertx, deviceRegistryEndpoint);

        for (int i = 1; i <= numberOfProjects; i++) {
            String projectName = String.format("project-%s", i);

            kubernetes.createNamespace(projectName);

            IoTProject project = IoTUtils.getBasicIoTProjectObject(projectName, projectName,
                    projectName, getDefaultAddressSpacePlan());
            isolatedIoTManager.createIoTProject(project);
            IoTProjectTestContext ctx = new IoTProjectTestContext(projectName, project);

            configureDeviceSide(ctx);

            configureAmqpSide(ctx);

            projects.add(ctx);
        }
    }

    @AfterEach
    void cleanEnv(ExtensionContext context) throws Exception {
        for (IoTProjectTestContext ctx : projects) {
            cleanDeviceSide(ctx);
            cleanAmqpSide(ctx);
        }
    }

    @Test
    @Tag(ACCEPTANCE)
    void testMultipleProjects() throws Exception {

        for (final IoTProjectTestContext ctx : projects) {
            try (var http = ctx.getHttpAdapterClient()) {
                new MessageSendTester()
                        .type(MessageSendTester.Type.TELEMETRY)
                        .delay(Duration.ofSeconds(1))
                        .consumerFactory(ConsumerFactory.of(ctx.getAmqpClient(), IoTUtils.getTenantId(ctx.getProject())))
                        .sender(http::send)
                        .amount(50)
                        .consume(MessageSendTester.Consume.BEFORE)
                        .execute();

                new MessageSendTester()
                        .type(MessageSendTester.Type.EVENT)
                        .delay(Duration.ofMillis(100))
                        .consumerFactory(ConsumerFactory.of(ctx.getAmqpClient(), IoTUtils.getTenantId(ctx.getProject())))
                        .sender(http::send)
                        .amount(5)
                        .consume(MessageSendTester.Consume.AFTER)
                        .execute();
            }
        }

    }

    private void configureAmqpSide(IoTProjectTestContext ctx) throws Exception {
        AddressSpace addressSpace = isolatedIoTManager.getAddressSpace(ctx.getNamespace(),
                ctx.getProject().getSpec().getDownstreamStrategy().getManagedStrategy().getAddressSpace().getName());
        User amqpUser = configureAmqpUser(ctx.getProject(), addressSpace);
        ctx.setAmqpUser(amqpUser);
        AmqpClient amqpClient = configureAmqpClient(addressSpace, amqpUser);
        ctx.setAmqpClient(amqpClient);
    }

    private User configureAmqpUser(IoTProject project, AddressSpace addressSpace) {
        String tenant = IoTUtils.getTenantId(project);

        User amqpUser = new UserBuilder()

                .withNewMetadata()
                .withName(String.format("%s.%s", addressSpace.getMetadata().getName(), project.getMetadata().getName()))
                .endMetadata()

                .withNewSpec()
                .withUsername(UUID.randomUUID().toString())
                .withNewAuthentication()
                .withPassword(Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)))
                .withType(UserAuthenticationType.password)
                .endAuthentication()
                .withAuthorization(Collections.singletonList(new UserAuthorizationBuilder()
                        .withAddresses(IOT_ADDRESS_TELEMETRY + "/" + tenant,
                                IOT_ADDRESS_TELEMETRY + "/" + tenant + "/*",
                                IOT_ADDRESS_EVENT + "/" + tenant,
                                IOT_ADDRESS_EVENT + "/" + tenant + "/*")
                        .withOperations(Operation.recv)
                        .build()))
                .endSpec()
                .build();
        kubernetes.getUserClient(project.getMetadata().getNamespace()).create(amqpUser);

        return amqpUser;
    }

    private AmqpClient configureAmqpClient(AddressSpace addressSpace, User user) throws Exception {
        LOGGER.warn("Amqp factory: " + getAmqpClientFactory());
        AmqpClient amqpClient = getAmqpClientFactory().createQueueClient(addressSpace);
        amqpClient.getConnectOptions()
                .setUsername(user.getSpec().getUsername())
                .setPassword(new String(Base64.getDecoder().decode(user.getSpec().getAuthentication().getPassword())));
        return amqpClient;
    }

    private void cleanAmqpSide(IoTProjectTestContext ctx) throws Exception {
        ctx.getAmqpClient().close();
        var userClient = kubernetes.getUserClient(ctx.getNamespace());
        userClient.withName(ctx.getAmqpUser().getMetadata().getName()).cascading(true).delete();
    }

    private void configureDeviceSide(IoTProjectTestContext ctx) throws Exception {
        String tenant = IoTUtils.getTenantId(ctx.getProject());
        ctx.setDeviceId(UUID.randomUUID().toString());
        ctx.setDeviceAuthId(UUID.randomUUID().toString());
        ctx.setDevicePassword(UUID.randomUUID().toString());
        registryClient.registerDevice(tenant, ctx.getDeviceId());
        credentialsClient.addCredentials(tenant, ctx.getDeviceId(), ctx.getDeviceAuthId(), ctx.getDevicePassword(), null, HttpURLConnection.HTTP_NO_CONTENT);
        Endpoint httpAdapterEndpoint = kubernetes.getExternalEndpoint("iot-http-adapter");
        ctx.setHttpAdapterClient(new HttpAdapterClient(null, httpAdapterEndpoint, ctx.getDeviceAuthId(), tenant, ctx.getDevicePassword()));
        IMqttClient mqttAdapterClient = new MqttClientFactory.Builder()
                .clientId(ctx.getDeviceId())
                .endpoint(kubernetes.getExternalEndpoint("iot-mqtt-adapter"))
                .usernameAndPassword(ctx.getDeviceAuthId() + "@" + tenant, ctx.getDevicePassword())
                .mqttConnectionOptions(options -> {
                    options.setAutomaticReconnect(true);
                    options.setConnectionTimeout(60);
                    options.setHttpsHostnameVerificationEnabled(false);
                })
                .create();
        TestUtils.waitUntilCondition("Successfully connect to mqtt adapter", phase -> {
            try {
                mqttAdapterClient.connect();
                return true;
            } catch (MqttException mqttException) {
                if (phase == WaitPhase.LAST_TRY) {
                    log.error("Error waiting to connect mqtt adapter", mqttException);
                }
                return false;
            }
        }, new TimeoutBudget(1, TimeUnit.MINUTES));
        log.info("Connection to mqtt adapter succeeded");
        ctx.setMqttAdapterClient(mqttAdapterClient);
    }

    private void cleanDeviceSide(IoTProjectTestContext ctx) throws Exception {
        String tenant = IoTUtils.getTenantId(ctx.getProject());
        String deviceId = ctx.getDeviceId();
        credentialsClient.deleteAllCredentials(tenant, deviceId);
        registryClient.deleteDeviceRegistration(tenant, deviceId);
        registryClient.getDeviceRegistration(tenant, deviceId, HttpURLConnection.HTTP_NOT_FOUND);
        ctx.getHttpAdapterClient().close();
        ctx.getMqttAdapterClient().close();
    }

}
