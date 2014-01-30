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
package com.cloud.rmap.dao;

import com.cloud.rmap.RmapVO;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.TransactionLegacy;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.ejb.Local;

@Component
@Local(value = {RmapDao.class})
public class RmapDaoImpl extends GenericDaoBase<RmapVO, Long> implements RmapDao {
    private static final Logger s_logger = Logger.getLogger(RmapDaoImpl.class);

    protected SearchBuilder<RmapVO> RegionUUIDSearch;
    protected SearchBuilder<RmapVO> SourceSearch;

    public RmapDaoImpl() {

        RegionUUIDSearch = createSearchBuilder();
        RegionUUIDSearch.and("source", RegionUUIDSearch.entity().getSource(), SearchCriteria.Op.EQ);
        RegionUUIDSearch.and("region_id", RegionUUIDSearch.entity().getRegionId(), SearchCriteria.Op.EQ);
        RegionUUIDSearch.done();

        SourceSearch = createSearchBuilder();
        SourceSearch.and("uuid", SourceSearch.entity().getUuid(), SearchCriteria.Op.EQ);
        SourceSearch.and("region_id", SourceSearch.entity().getRegionId(), SearchCriteria.Op.EQ);
        SourceSearch.done();
    }

    @Override
    public synchronized RmapVO create(RmapVO rmap) {
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        try {
            txn.start();
            persist(rmap);
            txn.commit();
            return rmap;
        } catch (Exception e) {
            s_logger.error("Unable to create rmap due to " + e.getMessage(), e);
            txn.rollback();
            return null;
        }
    }

    @Override
    public RmapVO findRmapBySource(String source, long regionId) {
        SearchCriteria<RmapVO> sc = RegionUUIDSearch.create();
        sc.setParameters("source", source);
        sc.setParameters("region_id", regionId);
        return findOneBy(sc);
    }

    @Override
    public RmapVO findSource(String uuid, long regionId) {
        SearchCriteria<RmapVO> sc = SourceSearch.create();
        sc.setParameters("uuid", uuid);
        sc.setParameters("region_id", regionId);
        return findOneBy(sc);
    }
}
