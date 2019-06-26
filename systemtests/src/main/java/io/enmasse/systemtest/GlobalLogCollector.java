/*
 * Copyright 2017-2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest;

import io.enmasse.address.model.AddressSpace;
import io.enmasse.systemtest.cmdclients.KubeCMDClient;
import io.enmasse.systemtest.executor.ExecutionResultData;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.fabric8.kubernetes.api.model.Pod;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class GlobalLogCollector {
    private final static Logger log = CustomLogger.getLogger();
    private final Map<String, LogCollector> collectorMap = new HashMap<>();
    private final Kubernetes kubernetes;
    private final File logDir;
    private final String namespace;

    public GlobalLogCollector(Kubernetes kubernetes, File logDir) {
        this.kubernetes = kubernetes;
        this.logDir = logDir;
        this.namespace = kubernetes.getInfraNamespace();
    }


    public synchronized void startCollecting(AddressSpace addressSpace) throws Exception {
        log.info("Start collecting logs for address space {}", addressSpace.getMetadata().getName());
        collectorMap.put(AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace), new LogCollector(kubernetes, new File(logDir, AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace)), AddressSpaceUtils.getAddressSpaceInfraUuid(addressSpace)));
    }

    public synchronized void stopCollecting(String namespace) throws Exception {
        log.info("Stop collecting logs for pods in namespace {}", namespace);
        LogCollector collector = collectorMap.get(namespace);
        if (collector != null) {
            collector.close();
        }
        collectorMap.remove(namespace);
    }

    public void collectConfigMaps() {
        collectConfigMaps("global");
    }

    public void collectConfigMaps(String operation) {
        log.info("Collecting configmaps for namespace {}", namespace);
        kubernetes.getAllConfigMaps(namespace).getItems().forEach(configMap -> {
            try {
                Path confMapFile = resolveLogFile(configMap.getMetadata().getName() + "." + operation + ".configmap");
                log.info("config map '{}' will be archived with path: '{}'", configMap.getMetadata().getName(), confMapFile);
                if (!Files.exists(confMapFile)) {
                    try (BufferedWriter bf = Files.newBufferedWriter(confMapFile)) {
                        bf.write(configMap.toString());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Collect logs from terminated pods in namespace
     */
    public void collectLogsTerminatedPods() {
        log.info("Store logs from all terminated pods in namespace '{}'", namespace);
        kubernetes.getLogsOfTerminatedPods(namespace).forEach((podName, podLogTerminated) -> {
            try {
                Path podLog = resolveLogFile(namespace + "." + podName + ".terminated.log");
                log.info("log of terminated '{}' pod will be archived with path: '{}'", podName, podLog);
                try (BufferedWriter bf = Files.newBufferedWriter(podLog)) {
                    bf.write(podLogTerminated);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public void collectLogsOfPodsByLabels(String namespace, String discriminator, Map<String, String> labels) {
        log.info("Store logs from all pods in namespace '{}' matching labels {}", namespace, labels);
        kubernetes.getLogsByLables(namespace, labels).forEach((podName, podLogs) -> {
            try {
                String filename = discriminator == null ? String.format("%s.%s.log", namespace, podName) : String.format("%s.%s.%s.log", namespace, discriminator, podName);
                Path podLog = resolveLogFile(filename);
                log.info("log of '{}' pod will be archived with path: '{}'", podName, podLog);
                try (BufferedWriter bf = Files.newBufferedWriter(podLog)) {
                    bf.write(podLogs);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public void collectEvents() throws IOException {
        long timestamp = System.currentTimeMillis();
        log.info("Collecting events in {}", namespace);
        ExecutionResultData result = KubeCMDClient.getEvents(namespace);
        Path eventLog = resolveLogFile(namespace + ".events." + timestamp);
        try (BufferedWriter writer = Files.newBufferedWriter(eventLog)) {
            writer.write(result.getStdOut());
        } catch (Exception e) {
            log.error("Error collecting events for {}", namespace, e);
        }
    }

    public void collectHttpAdapterQdrProxyState() {
        log.info("Collecting qdr-proxy router state in namespace {}", namespace);
        collectRouterState("httpAdapterQdrProxyState", System.currentTimeMillis(),
                kubernetes.listPods(Map.of("component", "iot", "name", "iot-http-adapter")).stream(),
                Optional.of("qdr-proxy"),
                this::qdrProxyCmd);
    }

    public void collectMqttAdapterQdrProxyState() {
        log.info("Collecting qdr-proxy router state in namespace {}", namespace);
        collectRouterState("mqttAdapterQdrProxyState", System.currentTimeMillis(),
                kubernetes.listPods(Map.of("component", "iot", "name", "iot-mqtt-adapter")).stream(),
                Optional.of("qdr-proxy"),
                this::qdrProxyCmd);
    }

    public void collectRouterState(String operation) {
        log.info("Collecting router state in namespace {}", namespace);
        collectRouterState(operation, System.currentTimeMillis(),
                kubernetes.listPods(Collections.singletonMap("capability", "router")).stream(),
                Optional.of("router"),
                this::enmasseRouterCmd);
    }

    private void collectRouterState(String operation, long timestamp, Stream<Pod> podsStream, Optional<String> container,
            BiFunction<String, String[], String[]> saslMechanismArgsCmdProvider) {
        podsStream.filter(pod -> pod.getStatus().getPhase().equals("Running"))
        .forEach(pod -> {
            collectRouterInfo(pod, container, "." + operation + ".autolinks." + timestamp, saslMechanismArgsCmdProvider.apply("qdmanage", new String[] {"QUERY", "--type=autoLink"}));
            collectRouterInfo(pod, container, "." + operation + ".links." + timestamp, saslMechanismArgsCmdProvider.apply("qdmanage", new String[] {"QUERY", "--type=link"}));
            collectRouterInfo(pod, container, "." + operation + ".connections." + timestamp, saslMechanismArgsCmdProvider.apply("qdmanage", new String[] {"QUERY", "--type=connection"}));
            collectRouterInfo(pod, container, "." + operation + ".qdstat_a." + timestamp, saslMechanismArgsCmdProvider.apply("qdstat", new String[] {"-a"}));
            collectRouterInfo(pod, container, "." + operation + ".qdstat_l." + timestamp, saslMechanismArgsCmdProvider.apply("qdstat", new String[] {"-l"}));
            collectRouterInfo(pod, container, "." + operation + ".qdstat_n." + timestamp, saslMechanismArgsCmdProvider.apply("qdstat", new String[] {"-n"}));
            collectRouterInfo(pod, container, "." + operation + ".qdstat_c." + timestamp, saslMechanismArgsCmdProvider.apply("qdstat", new String[] {"-c"}));
            collectRouterInfo(pod, container, "." + operation + ".qdstat_linkroutes." + timestamp, saslMechanismArgsCmdProvider.apply("qdstat", new String[] {"--linkroutes"}));
        });
    }

    private String[] qdrProxyCmd(String cmd, String...extraArgs) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add(cmd);
        allArgs.add("--sasl-mechanisms=ANONYMOUS");
        allArgs.add("-b");
        allArgs.add("127.0.0.1:5672");
        allArgs.addAll(Arrays.asList(extraArgs));
        return allArgs.toArray(String[]::new);
    }

    private String[] enmasseRouterCmd(String cmd, String... extraArgs) {
        List<String> allArgs = new ArrayList<>();
        allArgs.add(cmd);
/*
        allArgs.add("--sasl-mechanisms=EXTERNAL");
        allArgs.add("--ssl-certificate=/etc/enmasse-certs/tls.crt");
        allArgs.add("--ssl-key=/etc/enmasse-certs/tls.key");
        allArgs.add("--ssl-trustfile=/etc/enmasse-certs/ca.crt");
        allArgs.add("--ssl-disable-peer-name-verify");
*/
        allArgs.add("-b");
        allArgs.add("amqp://127.0.0.1:7777");
        allArgs.addAll(Arrays.asList(extraArgs));
        return allArgs.toArray(String[]::new);
    }

    private void collectRouterInfo(Pod pod, Optional<String> container, String filesuffix, String[] command) {
        String output = KubeCMDClient.runOnPod(
                pod.getMetadata().getNamespace(),
                pod.getMetadata().getName(),
                container,
                command).getStdOut();
        try {
            Path routerAutoLinks = resolveLogFile(pod.getMetadata().getName() + filesuffix);
            log.info("router info '{}' pod will be archived with path: '{}'", pod.getMetadata().getName(), routerAutoLinks);
            if (!Files.exists(routerAutoLinks)) {
                try (BufferedWriter bf = Files.newBufferedWriter(routerAutoLinks)) {
                    bf.write(output);
                }
            }
        } catch (IOException e) {
            log.warn("Error collecting router state", e);
        }
    }

    public void collectApiServerJmapLog() {
        log.info("Collecting jmap from api server");
        kubernetes.listPods(Collections.singletonMap("component", "api-server")).forEach(this::collectJmap);
    }

    private void collectJmap(Pod pod) {
        KubeCMDClient.runOnPod(
                pod.getMetadata().getNamespace(),
                pod.getMetadata().getName(),
                Optional.empty(),
                "api-server", "jmap", "-dump:live,format=b,file=/tmp/dump.bin", "1");
        try {
            Path jmapLog = resolveLogFile(pod.getMetadata().getName() + ".dump." + Instant.now().toString().replace(":", "_") + ".bin");
            KubeCMDClient.copyPodContent(pod.getMetadata().getName(), "/tmp/dump.bin", jmapLog.toAbsolutePath().toString());
        } catch (Exception e) {
            log.warn("Error collecting jmap state", e);
        }
    }

    /**
     * Create a new path inside the log directory, and ensure that the parent directory exists.
     *
     * @param other the path segment, relative to the log directory.
     * @return The full path.
     * @throws IOException In case of any IO error
     */
    private Path resolveLogFile(final String other) throws IOException {
        return Files
                .createDirectories(Paths.get(logDir.getPath(), namespace))
                .resolve(other);
    }

}
