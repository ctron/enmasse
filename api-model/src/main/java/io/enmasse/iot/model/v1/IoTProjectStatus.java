/*
 * Copyright 2019-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.model.v1;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.Doneable;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        inline = @Inline(
                type = Doneable.class,
                prefix = "Doneable",
                value = "done"
                )
        )
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IoTProjectStatus {

    private String tenantName;
    private ExternalDownstreamStrategy downstreamEndpoint;
    private List<ProjectCondition> conditions;
    private String phase;
    private String phaseReason;
    private AcceptedStatus accepted;
    private ManagedStatus managed;

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public ExternalDownstreamStrategy getDownstreamEndpoint() {
        return downstreamEndpoint;
    }

    public void setDownstreamEndpoint(ExternalDownstreamStrategy downstreamEndpoint) {
        this.downstreamEndpoint = downstreamEndpoint;
    }

    public List<ProjectCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ProjectCondition> conditions) {
        this.conditions = conditions;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhaseReason(String phaseReason) {
        this.phaseReason = phaseReason;
    }

    public String getPhaseReason() {
        return phaseReason;
    }

    public void setAccepted(AcceptedStatus accepted) {
        this.accepted = accepted;
    }

    public AcceptedStatus getAccepted() {
        return accepted;
    }

    public void setManaged(ManagedStatus managed) {
        this.managed = managed;
    }

    public ManagedStatus getManaged() {
        return managed;
    }

}
