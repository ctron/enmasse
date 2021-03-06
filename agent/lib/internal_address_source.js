/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

var http = require('http');
var util = require('util');
var events = require('events');
var kubernetes = require('./kubernetes.js');
var log = require('./log.js').logger();
var myutils = require('./utils.js');
var clone = require('clone');

function extract_spec(def, env) {
    if (def.spec === undefined) {
        console.error('no spec found on %j', def);
    }
    var o = myutils.merge({}, def.spec, {status:def.status});

    o.name = def.metadata ? def.metadata.name : def.address;
    o.addressSpace = def.metadata && def.metadata.annotations && def.metadata.annotations['addressSpace'] ? def.metadata.annotations['addressSpace'] : process.env.ADDRESS_SPACE;
    o.addressSpaceNamespace = def.metadata ? def.metadata.namespace : process.env.ADDRESS_SPACE_NAMESPACE;

    if (def.status && def.status.brokerStatuses) {
        o.allocated_to = clone(def.status.brokerStatuses);
    }
    return o;
}

function is_defined (addr) {
    return addr !== undefined;
}

function ready (addr) {
    return addr && addr.status && addr.status.phase !== 'Terminating' && addr.status.phase !== 'Pending';
}

function same_allocation(a, b) {
    if (a === b) {
        return true;
    } else if (a == null || b == null || a.length !== b.length) {
        return false;
    }
    for (var i in a) {
        var equal = false;
        for (var j in b) {
            if (a[i].containerId === b[j].containerId && a[i].clusterId === b[j].clusterId && a[i].state === b[j].state) {
                equal = true;
                break;
            }
        }
        if (!equal) {
            return false;
        }
    }
    return true;
}

function same_address_definition_and_allocation(a, b) {
    if (a.address === b.address && a.type === b.type && !same_allocation(a.allocated_to, b.allocated_to)) {
        log.info('allocation changed for %s %s: %s <-> %s', a.type, a.address, JSON.stringify(a.allocated_to), JSON.stringify(b.allocated_to));
    }
    return same_address_definition(a, b)
        && same_allocation(a.allocated_to, b.allocated_to)
        && same_plan_status(a.status ? a.status.planStatus : undefined, b.status ? b.status.planStatus : undefined)
        && myutils.same_ttl(a.status ? a.status.messageTtl : undefined, b.status ? b.status.messageTtl : undefined)
        && myutils.same_message_redelivery(a.status ? a.status.messageRedelivery : undefined, b.status ? b.status.messageRedelivery : undefined);
}

function same_address_definition(a, b) {
    if (a === b) return true;
    return a
        && b
        && a.address === b.address
        && a.type === b.type
        && a.plan === b.plan
        && a.deadletter === b.deadletter
        && a.expiry === b.expiry
        && myutils.same_ttl(a.messageTtl, b.messageTtl)
        && myutils.same_ttl(a.messageRedelivery, b.messageRedelivery);
}

function same_address_status(a, b) {
    if (a === b) return true;
    return a
        && b
        && a.isReady === b.isReady
        && a.phase === b.phase
        && myutils.same_status_messages(a.messages, b.messages)
        && same_plan_status(a.planStatus, b.planStatus)
        && myutils.same_ttl(a.messageTtl, b.messageTtl)
        && myutils.same_message_redelivery(a.messageRedelivery, b.messageRedelivery);

}

function same_address_definition_and_status(a, b) {
    return same_address_definition(a, b) && same_address_status(a.status, b.status);
}

function same_plan_status(a, b) {
    if (a === b) return true;
    return a && b && a.name === b.name && a.partitions === b.partitions && same_addressplan_resources(a.resources, b.resources);
}

function same_addressplan_resources(a, b) {
    if (a === b) return true;
    return a && b && a.broker === b.broker && a.router === b.router;
}

function address_compare(a, b) {
    return myutils.string_compare(a.name, b.name);
}

function by_address(a) {
    return a.address;
}

function description(list) {
    return myutils.description(list, by_address);
}

function AddressSource(config) {
    this.config = config || {};
    this.selector = "";
    events.EventEmitter.call(this);
}

AddressSource.prototype.start = function (addressplanssource) {
    var options = myutils.merge({selector: this.selector, namespace: this.config.ADDRESS_SPACE_NAMESPACE}, this.config);
    if (addressplanssource) {
        addressplanssource.on('addressplans_defined', this.updated_plans.bind(this))
    }

    this.watcher = kubernetes.watch('addresses', options);
    this.watcher.on('updated', this.updated.bind(this));
    this.readiness = {};
    this.last = {};
    this.plans = [];
};

util.inherits(AddressSource, events.EventEmitter);

AddressSource.prototype.get_changes = function (name, addresses, unchanged) {
    var c = myutils.changes(this.last[name], addresses, address_compare, unchanged, description);
    this.last[name] = clone(addresses);
    return c;
};

AddressSource.prototype.dispatch = function (name, addresses, description) {
    log.info('%s: %s', name, description);
    this.emit(name, addresses);
};

AddressSource.prototype.dispatch_if_changed = function (name, addresses, unchanged) {
    var changes = this.get_changes(name, addresses, unchanged);
    if (changes) {
        this.dispatch(name, addresses, changes.description);
    }
};

AddressSource.prototype.add_readiness_record = function (definition) {
    var record = this.readiness[definition.address];
    if (record === undefined) {
        record = {ready: false, address: definition.address, name: definition.name, type: definition.type};
        this.readiness[definition.address] = record;
    }
};

AddressSource.prototype.update_readiness_record = function (definition) {
    if (this.readiness[definition.address] !== undefined) {
        this.readiness[definition.address].ready = false;
    }
};

AddressSource.prototype.delete_readiness_record = function (definition) {
    delete this.readiness[definition.address];
};

AddressSource.prototype.update_readiness = function (changes) {
    if (changes.added.length > 0) {
        changes.added.forEach(this.add_readiness_record.bind(this));
    }
    if (changes.removed.length > 0) {
        changes.removed.forEach(this.delete_readiness_record.bind(this));
    }
    if (changes.modified.length > 0) {
        changes.modified.forEach(this.update_readiness_record.bind(this));
    }
};

AddressSource.prototype.updated_plans = function (plans) {
    this.plans = plans;
};

AddressSource.prototype.updated = function (objects) {
    log.debug('addresses updated: %j', objects);
    var self = this;
    var addresses = objects.filter(is_defined).filter(function (address) {
        return (self.config.ADDRESS_SPACE_PREFIX === undefined) || address.metadata.name.startsWith(self.config.ADDRESS_SPACE_PREFIX);
    }).map(extract_spec);
    var changes = this.get_changes('addresses_defined', addresses, same_address_definition_and_status);
    if (changes) {
        this.update_readiness(changes);
        this.dispatch('addresses_defined', addresses, changes.description);
        // used by standard
        this.dispatch_if_changed('addresses_ready', addresses.filter(ready), same_address_definition_and_allocation);
    }
};

AddressSource.prototype.update_status = function (record, ready) {
    var self = this;

    function sanitizeTtlValue(ttl) {
        return ttl && ttl < 1 ? undefined : ttl;
    }

    function update(address) {
        var updated = 0;
        var messages = [];
        if (address.status === undefined) {
            address.status = {};
        }

        var planDef;
        for (var p in self.plans) {
            if (self.plans[p].metadata.name === address.spec.plan) {
                planDef = self.plans[p];
                break
            }
        }
        if (planDef && planDef.spec && planDef.spec.addressType === address.spec.type) {
            if (address.status.planStatus === undefined) {
                address.status.planStatus = {};
            }
            if (planDef.metadata.name !== address.status.planStatus.name) {
                address.status.planStatus.name = planDef.metadata.name;
                updated++;
            }

            planDef.spec.partitions = 1; // Always 1 for brokered
            if (planDef.spec.partitions !== address.status.planStatus.partitions) {
                address.status.planStatus.partitions = planDef.spec.partitions;
                updated++;
            }
            if (planDef.spec.resources) {
                address.status.planStatus.resources = clone(planDef.spec.resources);
                updated++;
            } else if (address.status.planStatus.resources) {
                delete address.status.planStatus.resources;
                updated++;
            }

            var minimumTtl;
            var maximumTtl;
            if (planDef.spec && planDef.spec.messageTtl && planDef.spec.messageTtl.minimum) {
                minimumTtl = sanitizeTtlValue(planDef.spec.messageTtl.minimum);
            }
            if (planDef.spec && planDef.spec.messageTtl && planDef.spec.messageTtl.maximum) {
                maximumTtl = sanitizeTtlValue(planDef.spec.messageTtl.maximum);
            }
            if (maximumTtl && minimumTtl && minimumTtl > maximumTtl) {
                maximumTtl = undefined;
                minimumTtl = undefined;
            }

            var maximumDeliveryAttempts;
            var redeliveryDelay;
            var redeliveryDelayMultiplier;
            var maximumDeliveryDelay;
            if (planDef.spec && planDef.spec.messageRedelivery) {
                var messageRedelivery = planDef.spec.messageRedelivery;
                if (messageRedelivery.maximumDeliveryAttempts) {
                    maximumDeliveryAttempts = messageRedelivery.maximumDeliveryAttempts;
                }
                if (messageRedelivery.redeliveryDelay) {
                    redeliveryDelay = messageRedelivery.redeliveryDelay;
                }
                if (messageRedelivery.redeliveryDelayMultiplier) {
                    redeliveryDelayMultiplier = messageRedelivery.redeliveryDelayMultiplier;
                }
                if (messageRedelivery.maximumDeliveryDelay) {
                    maximumDeliveryDelay = messageRedelivery.maximumDeliveryDelay;
                }
            }

            if (address.spec.messageTtl && address.spec.messageTtl.minimum && (minimumTtl === undefined || address.spec.messageTtl.minimum > minimumTtl)) {
                minimumTtl = address.spec.messageTtl.minimum;
            }
            if (address.spec.messageTtl && address.spec.messageTtl.maximum && (maximumTtl === undefined || address.spec.messageTtl.maximum < maximumTtl)) {
                maximumTtl = address.spec.messageTtl.maximum;
            }
            if (maximumTtl && minimumTtl && minimumTtl > maximumTtl) {
                maximumTtl = undefined;
                minimumTtl = undefined;
            }

            if (address.spec.messageRedelivery) {
                var messageRedelivery = address.spec.messageRedelivery;
                if (messageRedelivery.maximumDeliveryAttempts) {
                    maximumDeliveryAttempts = messageRedelivery.maximumDeliveryAttempts;
                }
                if (messageRedelivery.redeliveryDelay) {
                    redeliveryDelay = messageRedelivery.redeliveryDelay;
                }
                if (messageRedelivery.redeliveryDelayMultiplier) {
                    redeliveryDelayMultiplier = messageRedelivery.redeliveryDelayMultiplier;
                }
                if (messageRedelivery.maximumDeliveryDelay) {
                    maximumDeliveryDelay = messageRedelivery.maximumDeliveryDelay;
                }
            }

            if (minimumTtl || maximumTtl) {
                address.status.messageTtl = {};
                if (address.status.messageTtl.minimum !== minimumTtl) {
                    address.status.messageTtl.minimum = minimumTtl;
                    updated++;
                }
                if (address.status.messageTtl.maximum !== maximumTtl) {
                    address.status.messageTtl.maximum = maximumTtl;
                    updated++;
                }
            } else {
                delete address.status.messageTtl;
                updated++;
            }

            if (maximumDeliveryAttempts || redeliveryDelay || redeliveryDelayMultiplier || maximumDeliveryDelay) {
                address.status.messageRedelivery = {};
                if (maximumDeliveryAttempts) {
                    address.status.messageRedelivery.maximumDeliveryAttempts = maximumDeliveryAttempts;
                    updated++;
                }
                if (redeliveryDelay) {
                    address.status.messageRedelivery.redeliveryDelay = redeliveryDelay;
                    updated++;
                }
                if (redeliveryDelayMultiplier) {
                    address.status.messageRedelivery.redeliveryDelayMultiplier = redeliveryDelayMultiplier;
                    updated++;
                }
                if (maximumDeliveryDelay) {
                    address.status.messageRedelivery.maximumDeliveryDelay = maximumDeliveryDelay;
                    updated++;
                }
            } else {
                delete address.status.messageRedelivery;
                updated++;
            }

            if (address.spec.expiry) {
                var targetRecord = myutils.values(self.readiness).find(record => record.address === address.spec.expiry);
                if (targetRecord) {
                    if (targetRecord.type !== "deadletter") {
                        ready = false;
                        messages.push(`Address '${address.spec.address}' (resource name '${address.metadata.name}') references an expiry address '${address.spec.expiry}' (resource name '${targetRecord.name}') that is not of expected type 'deadletter' (found type '${targetRecord.type}' instead).`);
                    }
                } else {
                    ready = false;
                    messages.push(`Address '${address.spec.address}' (resource name '${address.metadata.name}') references an expiry address '${address.spec.expiry}' that does not exist.`);
                }
            }
            if (address.spec.deadletter) {
                var targetRecord = myutils.values(self.readiness).find(record => record.address === address.spec.deadletter);
                if (targetRecord) {
                    if (targetRecord.type !== "deadletter") {
                        ready = false;
                        messages.push(`Address '${address.spec.address}' (resource name '${address.metadata.name}') references a deadletter address '${address.spec.deadletter}' (resource name '${targetRecord.name}') that is not of expected type 'deadletter' (found type '${targetRecord.type}' instead).`);
                    }
                } else {
                    ready = false;
                    messages.push(`Address '${address.spec.address}' (resource name '${address.metadata.name}') references a deadletter address '${address.spec.deadletter}' that does not exist.`);
                }
            }

        } else {
            ready = false;
            messages.push("Unknown address plan '" + address.spec.plan + "'");
            delete address.status.planStatus;
            updated++;
        }

        if (address.status.isReady !== ready) {
            address.status.isReady = ready;
            updated++;
        }

        var phase = ready ? 'Active' : 'Pending';
        if (address.status.phase  !== phase) {
            address.status.phase = phase;
            updated++;
        }

        if (!myutils.same_status_messages(address.status.messages, messages)) {
            address.status.messages = messages;
            updated++;
        }

        if (!("annotations" in address.metadata) || !("enmasse.io/version" in address.metadata.annotations) || (address.metadata.annotations['enmasse.io/version'] !== self.config.VERSION)) {
            address.metadata.annotations = myutils.merge({"enmasse.io/version": self.config.VERSION}, address.metadata.annotations);
            updated++;
        }
        return updated ? address : undefined;
    }
    var options = {namespace: this.config.ADDRESS_SPACE_NAMESPACE};
    Object.assign(options, this.config);
    return kubernetes.update('addresses/' + record.name, update, options).then(function (result) {
        if (result === 200) {
            record.ready = ready;
            log.info('updated status for %s to %s: %s', record.address, record.ready, result);
        } else if (result === 304) {
            record.ready = ready;
            log.debug('no need to update status for %j: %s', record, result);
        } else {
            log.error('failed to update status for %j: %s', record, result);
        }
    }).catch(function (error) {
        log.error('failed to update status for %j: %j', record, error);
    });
};

AddressSource.prototype.check_status = function (address_stats) {
    var results = [];
    for (var address in this.readiness) {
        var record = this.readiness[address];
        var stats = address_stats[address];
        if (stats === undefined) {
            log.info('no stats supplied for %s (%s)', address, record.ready);
        } else {
            if (!record.ready) {
                if (stats.propagated === 100) {
                    log.info('%s is now ready', address);
                    results.push(this.update_status(record, true));
                }
            } else {
                if (stats.propagated !== 100) {
                    log.info('%s is no longer ready', address);
                    results.push(this.update_status(record, false));
                }
            }
        }
    }
    return Promise.all(results);
};

AddressSource.prototype.check_address_plans = function () {
    log.info('Address space plan or address plan(s) updated');
    // Change the readiness to false so the next check_status will cause the status to be updated.
    for (var address in this.readiness) {
        var record = this.readiness[address];
        record.ready = false;
    }
};

AddressSource.prototype.create_address = function (definition, access_token) {
    var address_name = this.config.ADDRESS_SPACE + "." + myutils.kubernetes_name(definition.address);
    var address = {
        apiVersion: 'enmasse.io/v1beta1',
        kind: 'Address',
        metadata: {
            name: address_name,
            namespace: this.config.ADDRESS_SPACE_NAMESPACE,
            addressSpace: this.config.ADDRESS_SPACE
        },
        spec: {
            address: definition.address,
            type: definition.type,
            plan: definition.plan
        }
    };
    if (definition.type === 'subscription') {
        address.spec.topic = definition.topic;
    }

    var options = {token : access_token,
                   namespace: this.config.ADDRESS_SPACE_NAMESPACE};
    Object.assign(options, this.config);
    return kubernetes.post('addresses', address, options).then(function (result, error) {
        if (result >= 300) {
            log.error('failed to create address for %j [%d %s]: %s', address, result, http.STATUS_CODES[result], error);
            return Promise.reject(new Error(util.format('Failed to create address %j: %d %s %s', definition, result, http.STATUS_CODES[result], error)));
        } else {
            return Promise.resolve();
        }
    });
};

AddressSource.prototype.delete_address = function (definition, access_token) {
    var address_name = definition.name;
    var options = {token : access_token,
                   namespace: this.config.ADDRESS_SPACE_NAMESPACE};
    Object.assign(options, this.config);
    return kubernetes.delete_resource('addresses/' + address_name, options);
};

module.exports = AddressSource;
