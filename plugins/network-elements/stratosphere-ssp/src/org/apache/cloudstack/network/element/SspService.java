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
package org.apache.cloudstack.network.element;

import org.apache.cloudstack.api.commands.AddSspCmd;
import org.apache.cloudstack.api.commands.DeleteSspCmd;

import com.cloud.host.Host;
import com.cloud.utils.component.PluggableService;

/**
 * Stratosphere sdn platform plugin service.
 */
public interface SspService extends PluggableService {
    /**
     * Add an ssp api endpoint information as an host
     *
     * Adds a api endpoint information in database and make it usable.
     *
     * @param cmd
     * @return
     */
    public Host addSspHost(AddSspCmd cmd);

    /**
     * Delete an ssp api endpoint information
     *
     * Deletes the information from database.
     *
     * @param cmd
     * @return
     */
    public boolean deleteSspHost(DeleteSspCmd cmd);
}
