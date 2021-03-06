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
package com.cloud.ha;

import java.util.List;

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceManager;
import com.cloud.simulator.dao.MockConfigurationDao;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;

@Local(value = Investigator.class)
public class SimulatorInvestigator extends AdapterBase implements Investigator {
    private final static Logger s_logger = Logger.getLogger(SimulatorInvestigator.class);
    @Inject
    AgentManager _agentMgr;
    @Inject
    ResourceManager _resourceMgr;
    @Inject
    MockConfigurationDao _mockConfigDao;

    protected SimulatorInvestigator() {
    }

    @Override
    public Status isAgentAlive(Host agent) {
        if (agent.getHypervisorType() != HypervisorType.Simulator) {
            return null;
        }

        CheckOnHostCommand cmd = new CheckOnHostCommand(agent);
        List<HostVO> neighbors = _resourceMgr.listHostsInClusterByStatus(agent.getClusterId(), Status.Up);
        for (HostVO neighbor : neighbors) {
            if (neighbor.getId() == agent.getId() || neighbor.getHypervisorType() != Hypervisor.HypervisorType.Simulator) {
                continue;
            }
            try {
                Answer answer = _agentMgr.easySend(neighbor.getId(), cmd);
                if (answer != null) {
                    return answer.getResult() ? Status.Up : Status.Down;
                }
            } catch (Exception e) {
                s_logger.debug("Failed to send command to host: " + neighbor.getId());
            }
        }

        return null;
    }

    @Override
    public Boolean isVmAlive(VirtualMachine vm, Host host) {
        CheckVirtualMachineCommand cmd = new CheckVirtualMachineCommand(vm.getInstanceName());
        try {
            Answer answer = _agentMgr.send(vm.getHostId(), cmd);
            if (!answer.getResult()) {
                s_logger.debug("Unable to get vm state on " + vm.toString());
                return null;
            }
            CheckVirtualMachineAnswer cvmAnswer = (CheckVirtualMachineAnswer)answer;
            s_logger.debug("Agent responded with state " + cvmAnswer.getState().toString());
            return cvmAnswer.getState() == State.Running;
        } catch (AgentUnavailableException e) {
            s_logger.debug("Unable to reach the agent for " + vm.toString() + ": " + e.getMessage());
            return null;
        } catch (OperationTimedoutException e) {
            s_logger.debug("Operation timed out for " + vm.toString() + ": " + e.getMessage());
            return null;
        }
    }
}
