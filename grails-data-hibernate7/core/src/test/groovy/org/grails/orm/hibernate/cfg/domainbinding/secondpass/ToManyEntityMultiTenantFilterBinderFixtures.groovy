/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

/**
 * Domain classes of the former {@code ToManyEntityMultiTenantFilterBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class CMTBBidirectionalOwner {
    Long id
    static hasMany = [items: CMTBBidirectionalItem]
}

@Entity
class CMTBBidirectionalItem implements MultiTenant<CMTBBidirectionalItem> {
    Long id
    Long tenantId
    CMTBBidirectionalOwner owner
    static belongsTo = [owner: CMTBBidirectionalOwner]
}

@Entity
class CMTBUnidirectionalOwner {
    Long id
    static hasMany = [items: CMTBUnidirectionalItem]
}

@Entity
class CMTBUnidirectionalItem implements MultiTenant<CMTBUnidirectionalItem> {
    Long id
    Long tenantId
}

@Entity
class CMTBNonTenantOwner {
    Long id
    static hasMany = [items: CMTBNonTenantItem]
}

@Entity
class CMTBNonTenantItem {
    Long id
    String name
}

@Entity
class CMTBManyToManyOwner {
    Long id
    static hasMany = [items: CMTBManyToManyItem]
}

@Entity
class CMTBManyToManyItem implements MultiTenant<CMTBManyToManyItem> {
    Long id
    Long tenantId
    static hasMany = [owners: CMTBManyToManyOwner]
    static belongsTo = [CMTBManyToManyOwner]
}
