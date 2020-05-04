/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package state

import (
	"time"

	"github.com/enmasseproject/enmasse/pkg/amqpcommand"
)

type RouterState struct {
	host          string
	port          int32
	initialized   bool
	nextResync    time.Time
	commandClient amqpcommand.Client
	entities      map[RouterEntityType]map[string]RouterEntity
}

type RouterEntityType string

const (
	RouterAddressEntity   RouterEntityType = "org.apache.qpid.dispatch.router.config.address"
	RouterListenerEntity  RouterEntityType = "org.apache.qpid.dispatch.listener"
	RouterConnectorEntity RouterEntityType = "org.apache.qpid.dispatch.connector"
	RouterAutoLinkEntity  RouterEntityType = "org.apache.qpid.dispatch.router.config.autoLink"
)

type RouterEntity interface {
	Type() RouterEntityType
	GetName() string
	Equals(RouterEntity) bool
}

type NamedEntity struct {
	EntityType RouterEntityType
	Name       string
}

type RouterConnector struct {
	Name               string `json:"name"`
	Host               string `json:"host"`
	Port               string `json:"port"`
	Role               string `json:"role,omitempty"`
	SslProfile         string `json:"sslProfile,omitempty"`
	SaslMechanisms     string `json:"saslMechanisms,omitempty"`
	SaslUsername       string `json:"saslUsername,omitempty"`
	SaslPassword       string `json:"saslPassword,omitempty"`
	LinkCapacity       int    `json:"linkCapacity,omitempty"`
	IdleTimeoutSeconds int    `json:"idleTimeoutSeconds,omitempty"`
	VerifyHostname     bool   `json:"verifyHostname,omitempty"`
	PolicyVhost        string `json:"policyVhost,omitempty"`
	ConnectionStatus   string `json:"connectionStatus,omitempty"`
	ConnectionMsg      string `json:"connectionMsg,omitempty"`
}

type RouterListener struct {
	Name               string `json:"name"`
	Host               string `json:"host"`
	Port               string `json:"port"`
	Role               string `json:"role,omitempty"`
	SslProfile         string `json:"sslProfile,omitempty"`
	SaslMechanisms     string `json:"saslMechanisms,omitempty"`
	LinkCapacity       int    `json:"linkCapacity,omitempty"`
	IdleTimeoutSeconds int    `json:"idleTimeoutSeconds,omitempty"`
	PolicyVhost        string `json:"policyVhost,omitempty"`
	MultiTenant        bool   `json:"multiTenant,omitempty"`
}

type RouterVhost struct {
	Host string `json:"host"`
}

type RouterAddress struct {
	Name         string `json:"name"`
	Prefix       string `json:"prefix"`
	Waypoint     bool   `json:"waypoint"`
	Distribution string `json:"distribution"`
}

type RouterAutoLink struct {
	Name            string `json:"name"`
	Address         string `json:"address"`
	Direction       string `json:"direction"`
	Connection      string `json:"connection,omitempty"`
	ExternalAddress string `json:"externalAddress,omitempty"`
}

type RouterLinkRoute struct {
	Prefix      string `json:"string"`
	ContainerId string `json:"containerId"`
}