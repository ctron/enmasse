/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package agent

import (
	"encoding/json"
)

type AgentEventType string

const (
	AgentEventTypeRestart        = "restart"
	AgentEventInsertOrUpdateType = "insertOrUpdate"
	AgentEventTypeDelete         = "delete"
)

type AgentEvent struct {
	InfraUuid             string
	AddressSpace          string
	AddressSpaceNamespace string
	Type                  AgentEventType
	Object                interface{}
}

type AgentAddress struct {
	Name                  string
	AddressSpace          string
	AddressSpaceNamespace string
	Address               string
	Depth                 int
	MessagesIn            int `json:"messages_in"`
	MessagesOut           int `json:"messages_out"`
	Senders               int
	Receivers             int
	Shards                []AgentAddressShards
}

type AgentAddressShards struct {
	Name         string
	Consumers    int
	Enqueued     int
	Acknowledged int
	Killed       int
}

type AgentConnection struct {
	Id                    string
	Uuid                  string
	Host                  string
	Container             string
	AddressSpace          string
	AddressSpaceNamespace string
	AddressSpaceType      string
	Properties            map[string]string
	Encrypted             bool
	SaslMechanism         string `json:"sasl_mechanism"`
	User                  string
	CreationTimestamp     int64 `json:"creationTimestamp"`
	MessagesIn            int   `json:"messages_in"`
	MessagesOut           int   `json:"messages_out"`
	Outcomes              map[string]AgentOutcome
	Senders               []AgentAddressLink
	Receivers             []AgentAddressLink
	LastUpdated           int64
}

type AgentOutcome struct {
	Accepted    int
	Released    int
	Rejected    int
	Modified    int
	Unsettled   int
	Presettled  int
	Undelivered int
	Links       []AgentLink
}

type AgentLink struct {
	Identity         string
	Name             string
	OperStatus       string
	AdminStatus      string
	DeliveryCount    int
	Capacity         int
	Backlog          int
	RouterName       string
	ClientName       string
	AcceptedCount    int
	ReleasedCount    int
	ModifiedCount    int
	UnsettledCount   int
	PresettledCount  int
	UndeliveredCount int
	LastUpdated      int64
}

type AgentAddressLink struct {
	Address string
	Name    interface{}
	Uuid    string
	/* Populated for Standard */
	Accepted    int
	Released    int
	Rejected    int
	Modified    int
	Unsettled   int
	Undelivered int
	Presettled  int
	Links       []AgentLink /* the individual links, >1 for the fanout cases */
	/* Populated for Standard and Brokered */
	Deliveries int
}

func FromAgentConnectionBody(agentConnectionMap map[string]interface{}) (*AgentConnection, error) {
	for k, v := range agentConnectionMap {
		if vv, ok := v.(map[interface{}]interface{}); ok && len(vv) == 0 {
			delete(agentConnectionMap, k)
		}
	}

	bytes, e := json.Marshal(agentConnectionMap)
	if e != nil {
		return nil, e
	}

	m := AgentConnection{}
	err := json.Unmarshal(bytes, &m)
	if err == nil {
		return &m, nil
	} else {
		return nil, err
	}
}

func FromAgentAddressBody(agentAddressMap map[string]interface{}) (*AgentAddress, error) {
	cleanMap(agentAddressMap)

	bytes, e := json.Marshal(agentAddressMap)
	if e != nil {
		cleanMap(agentAddressMap)
		return nil, e
	}

	m := AgentAddress{}
	err := json.Unmarshal(bytes, &m)
	if err == nil {
		return &m, nil
	} else {
		return nil, err
	}

}

func cleanMap(m map[string]interface{}) {
	for k, v := range m {
		if vv, ok := v.(map[interface{}]interface{}); ok && len(vv) == 0 {
			delete(m, k)
		} else if vv, ok := v.(map[string]interface{}); ok {
			if len(vv) == 0 {
				delete(m, k)
			} else {
				cleanMap(vv)
			}
		}
	}
}
