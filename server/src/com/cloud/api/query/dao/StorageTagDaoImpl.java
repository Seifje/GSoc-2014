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
package com.cloud.api.query.dao;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import org.apache.cloudstack.api.response.StorageTagResponse;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import com.cloud.api.query.vo.StorageTagVO;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
@Local(value = {StorageTagDao.class})
public class StorageTagDaoImpl extends GenericDaoBase<StorageTagVO, Long> implements StorageTagDao {
    public static final Logger s_logger = Logger.getLogger(StorageTagDaoImpl.class);

    @Inject
    private ConfigurationDao _configDao;

    private final SearchBuilder<StorageTagVO> spSearch;

    private final SearchBuilder<StorageTagVO> spIdSearch;

    protected StorageTagDaoImpl() {

        spSearch = createSearchBuilder();
        spSearch.and("idIN", spSearch.entity().getId(), SearchCriteria.Op.IN);
        spSearch.done();

        spIdSearch = createSearchBuilder();
        spIdSearch.and("id", spIdSearch.entity().getId(), SearchCriteria.Op.EQ);
        spIdSearch.done();

        _count = "select count(distinct id) from storage_tag_view WHERE ";// storage_tag_view
    }

    @Override
    public StorageTagResponse newStorageTagResponse(StorageTagVO tag) {
        StorageTagResponse tagResponse = new StorageTagResponse();

        tagResponse.setName(tag.getName());
        tagResponse.setPoolId(tag.getPoolId());

        tagResponse.setObjectName("storagetag");
        return tagResponse;
    }

    @Override
    public List<StorageTagVO> searchByIds(Long... spIds) {
        // set detail batch query size
        int DETAILS_BATCH_SIZE = 2000;
        String batchCfg = _configDao.getValue("detail.batch.query.size");
        if (batchCfg != null) {
            DETAILS_BATCH_SIZE = Integer.parseInt(batchCfg);
        }
        // query details by batches
        List<StorageTagVO> uvList = new ArrayList<StorageTagVO>();
        // query details by batches
        int curr_index = 0;
        if (spIds.length > DETAILS_BATCH_SIZE) {
            while ((curr_index + DETAILS_BATCH_SIZE) <= spIds.length) {
                Long[] ids = new Long[DETAILS_BATCH_SIZE];
                for (int k = 0, j = curr_index; j < curr_index + DETAILS_BATCH_SIZE; j++, k++) {
                    ids[k] = spIds[j];
                }
                SearchCriteria<StorageTagVO> sc = spSearch.create();
                sc.setParameters("idIN", ids);
                List<StorageTagVO> vms = searchIncludingRemoved(sc, null, null, false);
                if (vms != null) {
                    uvList.addAll(vms);
                }
                curr_index += DETAILS_BATCH_SIZE;
            }
        }
        if (curr_index < spIds.length) {
            int batch_size = (spIds.length - curr_index);
            // set the ids value
            Long[] ids = new Long[batch_size];
            for (int k = 0, j = curr_index; j < curr_index + batch_size; j++, k++) {
                ids[k] = spIds[j];
            }
            SearchCriteria<StorageTagVO> sc = spSearch.create();
            sc.setParameters("idIN", ids);
            List<StorageTagVO> vms = searchIncludingRemoved(sc, null, null, false);
            if (vms != null) {
                uvList.addAll(vms);
            }
        }
        return uvList;
    }

}
