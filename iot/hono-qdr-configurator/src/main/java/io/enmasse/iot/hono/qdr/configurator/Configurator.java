/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.hono.qdr.configurator;

import static io.enmasse.iot.model.v1.Project.resourceDefintion;

import io.enmasse.iot.model.v1.DoneableProject;
import io.enmasse.iot.model.v1.Project;
import io.enmasse.iot.model.v1.ProjectList;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.client.DefaultOpenShiftClient;

public class Configurator implements AutoCloseable, Runnable {

    private DefaultOpenShiftClient client;

    private MixedOperation<Project, ProjectList, DoneableProject, Resource<Project, DoneableProject>> projectClient;

    public Configurator() {
        this.client = new DefaultOpenShiftClient();

        this.projectClient = this.client.customResource(resourceDefintion(), Project.class, ProjectList.class, DoneableProject.class);
        this.projectClient.inAnyNamespace().watch(new Watcher<Project>() {

            @Override
            public void eventReceived(final Action action, final Project resource) {
            }

            @Override
            public void onClose(final KubernetesClientException cause) {
            }
        });
    }

    @Override
    public void close() {
        this.client.close();
    }

    @Override
    public void run() {
    }
}
