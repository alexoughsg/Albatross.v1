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
import com.cloud.utils.db.GenericDao;

public interface RmapDao extends GenericDao<RmapVO, Long>
{
    public RmapVO create(RmapVO rmap);

    /*public DomainVO findDomainByPath(String domainPath);

    public boolean isChildDomain(Long parentId, Long childId);

    DomainVO findImmediateChildForParent(Long parentId);

    List<DomainVO> findImmediateChildrenForParent(Long parentId);

    List<DomainVO> findAllChildren(String path, Long parentId);

    List<DomainVO> findInactiveDomains();

    Set<Long> getDomainParentIds(long domainId);

    List<Long> getDomainChildrenIds(String path);*/

    public RmapVO findRmapBySource(String source, long regionId);
    public RmapVO findSource(String uuid, long regionId);
}
