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

import grails.gorm.annotation.Entity

/**
 * Domain classes of the former {@code CollectionKeyBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class CKBBidOwner {
    Long id
    static hasMany = [items: CKBBidItem]
}

@Entity
class CKBBidItem {
    Long id
    CKBBidOwner owner
    static belongsTo = [owner: CKBBidOwner]
}

@Entity
class CKBManyToManyOwner {
    Long id
    static hasMany = [items: CKBManyToManyItem]
}

@Entity
class CKBManyToManyItem {
    Long id
    static hasMany = [owners: CKBManyToManyOwner]
    static belongsTo = [CKBManyToManyOwner]
}

@Entity
class CKBUniOwner {
    Long id
    static hasMany = [items: CKBUniItem]
}

@Entity
class CKBUniItem {
    Long id
    String description
}

@Entity
class CKBJoinKeyOwner {
    Long id
    static hasMany = [items: CKBJoinKeyItem]
    static mapping = {
        items joinTable: [key: 'owner_fk']
    }
}

@Entity
class CKBJoinKeyItem {
    Long id
    String description
}

@Entity
class CKBCompositeOwner implements Serializable {
    String name
    Integer code
    static hasMany = [items: CKBCompositeItem]
    static mapping = {
        id composite: ['name', 'code']
    }
}

@Entity
class CKBCompositeItem {
    Long id
    String val
}
