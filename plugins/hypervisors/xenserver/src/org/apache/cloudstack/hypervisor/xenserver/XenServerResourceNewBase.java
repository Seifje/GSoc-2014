// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.hypervisor.xenserver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Event;
import com.xensource.xenapi.EventBatch;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.Pool;
import com.xensource.xenapi.Task;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.Types.XenAPIException;
import com.xensource.xenapi.VM;

import com.cloud.agent.api.StartupCommand;
import com.cloud.hypervisor.xenserver.resource.XenServer620SP1Resource;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineName;

/**
 *
 * XenServerResourceNewBase is an abstract base class that encapsulates how
 * CloudStack should interact with XenServer after a special XenServer 6.2
 * hotfix. From here on, every Resource for future versions of XenServer should
 * use this as the base class. This base class lessens the amount of load
 * CloudStack places on Xapi because it doesn't use polling as a means to
 * collect data and figure out task completion.
 *
 * This base class differs from CitrixResourceBase in the following ways: - VM
 * states are detected using Event.from instead of polling. This increases the
 * number of threads CloudStack uses but the threads are mostly idle just
 * waiting for events from XenServer. - stats are collected through the http
 * interface rather than Xapi plugin. This change may be promoted to
 * CitrixResourceBase as it's also possible in previous versions of XenServer. -
 * Asynchronous task completion is done throught Event.from rather than polling.
 *
 */
public class XenServerResourceNewBase extends XenServer620SP1Resource {
    private static final Logger s_logger = Logger.getLogger(XenServerResourceNewBase.class);
    protected VmEventListener _listener = null;

    @Override
    public StartupCommand[] initialize() throws IllegalArgumentException {
        StartupCommand[] cmds = super.initialize();

        Connection conn = getConnection();
        Pool pool;
        try {
            pool = Pool.getByUuid(conn, _host.pool);
            Pool.Record poolr = pool.getRecord(conn);

            Host.Record masterRecord = poolr.master.getRecord(conn);
            if (_host.uuid.equals(masterRecord.uuid)) {
                _listener = new VmEventListener(true);

                //
                // TODO disable event listener for now. Wait until everything
                // else is ready
                //

                // _listener.start();
            } else {
                _listener = new VmEventListener(false);
            }
        } catch (XenAPIException e) {
            throw new CloudRuntimeException("Unable to determine who is the master", e);
        } catch (XmlRpcException e) {
            throw new CloudRuntimeException("Unable to determine who is the master", e);
        }
        return cmds;
    }

    protected void waitForTask2(Connection c, Task task, long pollInterval, long timeout) throws XenAPIException, XmlRpcException, TimeoutException {
        long beginTime = System.currentTimeMillis();
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Task " + task.getNameLabel(c) + " (" + task.getType(c) + ") sent to " + c.getSessionReference() + " is pending completion with a " + timeout
                    + "ms timeout");
        }
        Set<String> classes = new HashSet<String>();
        classes.add("Task/" + task.toWireString());
        String token = "";
        Double t = new Double(timeout / 1000);
        while (true) {
            EventBatch map = Event.from(c, classes, token, t);
            token = map.token;
            @SuppressWarnings("unchecked")
            Set<Event.Record> events = map.events;
            if (events.size() == 0) {
                String msg = "No event for task " + task.toWireString();
                s_logger.warn(msg);
                task.cancel(c);
                throw new TimeoutException(msg);
            }
            for (Event.Record rec : events) {
                if (!(rec.snapshot instanceof Task.Record)) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Skipping over " + rec);
                    }
                    continue;
                }

                Task.Record taskRecord = (Task.Record)rec.snapshot;

                if (taskRecord.status != Types.TaskStatusType.PENDING) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Task, ref:" + task.toWireString() + ", UUID:" + taskRecord.uuid + " is done " + taskRecord.status);
                    }
                    return;
                } else {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Task: ref:" + task.toWireString() + ", UUID:" + taskRecord.uuid + " progress: " + taskRecord.progress);
                    }

                }
            }
            if (System.currentTimeMillis() - beginTime > timeout) {
                String msg = "Async " + timeout / 1000 + " seconds timeout for task " + task.toString();
                s_logger.warn(msg);
                task.cancel(c);
                throw new TimeoutException(msg);
            }
        }
    }

    protected class VmEventListener extends Thread {
        boolean _stop = false;
        HashMap<String, Pair<String, VirtualMachine.State>> _changes = new HashMap<String, Pair<String, VirtualMachine.State>>();
        boolean _isMaster;
        Set<String> _classes;
        String _token = "";

        public VmEventListener(boolean isMaster) {
            _isMaster = isMaster;
            _classes = new HashSet<String>();
            _classes.add("VM");
        }

        @Override
        public void run() {
            setName("XS-Listener-" + _host.ip);
            while (!_stop) {
                try {
                    Connection conn = getConnection();
                    EventBatch results;
                    try {
                        results = Event.from(conn, _classes, _token, new Double(30));
                    } catch (Exception e) {
                        s_logger.error("Retrying the waiting on VM events due to: ", e);
                        continue;
                    }

                    _token = results.token;
                    @SuppressWarnings("unchecked")
                    Set<Event.Record> events = results.events;
                    for (Event.Record event : events) {
                        try {
                            if (!(event.snapshot instanceof VM.Record)) {
                                if (s_logger.isDebugEnabled()) {
                                    s_logger.debug("The snapshot is not a VM: " + event);
                                }
                                continue;
                            }
                            VM.Record vm = (VM.Record)event.snapshot;

                            String hostUuid = null;
                            if (vm.residentOn != null && !vm.residentOn.toWireString().contains("OpaqueRef:NULL")) {
                                hostUuid = vm.residentOn.getUuid(conn);
                            }
                            recordChanges(conn, vm, hostUuid);
                        } catch (Exception e) {
                            s_logger.error("Skipping over " + event, e);
                        }
                    }
                } catch (Throwable th) {
                    s_logger.error("Exception caught in eventlistener thread: ", th);
                }
            }
        }

        protected void recordChanges(Connection conn, VM.Record rec, String hostUuid) {
            String vm = rec.nameLabel;
            if (!VirtualMachineName.isValidCloudStackVmName(vm, _instance)) {
                s_logger.debug("Skipping over VMs that does not conform to CloudStack naming convention: " + vm);
                return;
            }

            VirtualMachine.State currentState = convertToState(rec.powerState);
            if (vm.startsWith("migrating")) {
                s_logger.warn("Skipping " + vm + " because it is migrating.");
                return;
            }

            if (currentState == VirtualMachine.State.Stopped) {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Double check the power state to make sure we got the correct state for " + vm);
                }
                currentState = getRealPowerState(conn, vm);
            }

            boolean updateMap = false;
            boolean reportChange = false;

            // NOTE: For now we only record change when the VM is stopped. We
            // don't find out any VMs starting for now.
            synchronized (_cluster.intern()) {
                Pair<String, VirtualMachine.State> oldState = s_vms.get(_cluster, vm);
                if (oldState == null) {
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("Unable to find " + vm + " from previous map.  Assuming it was in Stopped state.");
                    }
                    oldState = new Pair<String, VirtualMachine.State>(null, VirtualMachine.State.Stopped);
                }

                if (s_logger.isTraceEnabled()) {
                    s_logger.trace(vm + ": current state=" + currentState + ", previous state=" + oldState);
                }

                if (oldState.second() == VirtualMachine.State.Starting) {
                    if (currentState == VirtualMachine.State.Running) {
                        updateMap = true;
                        reportChange = false;
                    } else if (currentState == VirtualMachine.State.Stopped) {
                        updateMap = false;
                        reportChange = false;
                    }
                } else if (oldState.second() == VirtualMachine.State.Migrating) {
                    updateMap = true;
                    reportChange = false;
                } else if (oldState.second() == VirtualMachine.State.Stopping) {
                    if (currentState == VirtualMachine.State.Stopped) {
                        updateMap = true;
                        reportChange = false;
                    } else if (currentState == VirtualMachine.State.Running) {
                        updateMap = false;
                        reportChange = false;
                    }
                } else if (oldState.second() != currentState) {
                    updateMap = true;
                    reportChange = true;
                } else if (hostUuid != null && !hostUuid.equals(oldState.first())) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Detecting " + vm + " moved from " + oldState.first() + " to " + hostUuid);
                    }
                    reportChange = true;
                    updateMap = true;
                }

                if (updateMap) {
                    s_vms.put(_cluster, hostUuid, vm, currentState);
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("Updated " + vm + " to [" + hostUuid + ", " + currentState);
                    }
                }
                if (reportChange) {
                    Pair<String, VirtualMachine.State> change = _changes.get(vm);
                    if (hostUuid == null) {
                        // This is really strange code. It looks like the sync
                        // code wants this to be set, which is extremely weird
                        // for VMs that are dead. Why would I want to set the
                        // hostUuid if the VM is stopped.
                        hostUuid = oldState.first();
                        if (hostUuid == null) {
                            hostUuid = _host.uuid;
                        }
                    }
                    if (change == null) {
                        change = new Pair<String, VirtualMachine.State>(hostUuid, currentState);
                    } else {
                        change.first(hostUuid);
                        change.second(currentState);
                    }
                    _changes.put(vm, change);
                }
            }
        }

        @Override
        public void start() {
            if (_isMaster) {
                // Throw away the initial set of events because they're history
                Connection conn = getConnection();
                EventBatch results;
                try {
                    results = Event.from(conn, _classes, _token, new Double(30));
                } catch (Exception e) {
                    s_logger.error("Retrying the waiting on VM events due to: ", e);
                    throw new CloudRuntimeException("Unable to start a listener thread to listen to VM events", e);
                }
                _token = results.token;
                s_logger.debug("Starting the event listener thread for " + _host.uuid);
                super.start();
            }
        }

        public boolean isListening() {
            return _isMaster;
        }

        public HashMap<String, Pair<String, VirtualMachine.State>> getChanges() {
            synchronized (_cluster.intern()) {
                if (_changes.size() == 0) {
                    return null;
                }
                HashMap<String, Pair<String, VirtualMachine.State>> diff = _changes;
                _changes = new HashMap<String, Pair<String, VirtualMachine.State>>();
                return diff;
            }
        }

        public void signalStop() {
            _stop = true;
            interrupt();
        }
    }

}
