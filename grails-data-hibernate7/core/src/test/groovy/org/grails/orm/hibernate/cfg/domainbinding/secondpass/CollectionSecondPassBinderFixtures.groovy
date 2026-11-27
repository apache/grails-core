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
import grails.gorm.hibernate.HibernateEntity

/**
 * Domain classes of the former {@code CollectionSecondPassBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class CSPBTestEntityWithMany implements HibernateEntity<CSPBTestEntityWithMany> {
    Long id
    String name
    static hasMany = [items: CSPBAssociatedItem]
}

@Entity
class CSPBAssociatedItem implements HibernateEntity<CSPBAssociatedItem> {
    Long id
    String value
    CSPBTestEntityWithMany parent
    static belongsTo = [parent: CSPBTestEntityWithMany]
}

@Entity
class CSPBHTMPOrder implements HibernateEntity<CSPBHTMPOrder> {
    Long id
    List<String> items = []
    static hasMany = [items: String]
}

@Entity
class CSPBUniOwner implements HibernateEntity<CSPBUniOwner> {
    Long id
    static hasMany = [items: CSPBUniItem]
}

@Entity
class CSPBUniItem implements HibernateEntity<CSPBUniItem> {
    Long id
    String name
}

@Entity
class CSPBManyToManyA implements HibernateEntity<CSPBManyToManyA> {
    Long id
    static hasMany = [others: CSPBManyToManyB]
}

@Entity
class CSPBManyToManyB implements HibernateEntity<CSPBManyToManyB> {
    Long id
    static hasMany = [owners: CSPBManyToManyA]
    static belongsTo = CSPBManyToManyA
}

@Entity
class CSPBOrderOwner implements HibernateEntity<CSPBOrderOwner> {
    Long id
    static hasMany = [items: CSPBOrderItem]
    static mapping = {
        items joinTable: [name: "ordered_items"], sort: "name", order: "desc"
    }
}

@Entity
class CSPBOrderItem implements HibernateEntity<CSPBOrderItem> {
    Long id
    String name
    CSPBOrderOwner owner
    static belongsTo = [owner: CSPBOrderOwner]
}

@Entity
class CSPBBidiOwner implements HibernateEntity<CSPBBidiOwner> {
    Long id
    static hasMany = [items: CSPBBidiItem]
}
@Entity
class CSPBBidiItem implements HibernateEntity<CSPBBidiItem> {
    Long id
    CSPBBidiOwner owner
    static belongsTo = [owner: CSPBBidiOwner]
}

@Entity
class CSPBMapOwner implements HibernateEntity<CSPBMapOwner> {
    Long id
    Map<String, CSPBMapItem> items
    static hasMany = [items: CSPBMapItem]
}

@Entity
class CSPBMapItem implements HibernateEntity<CSPBMapItem> {
    Long id
    CSPBMapOwner owner
    static belongsTo = [owner: CSPBMapOwner]
}
