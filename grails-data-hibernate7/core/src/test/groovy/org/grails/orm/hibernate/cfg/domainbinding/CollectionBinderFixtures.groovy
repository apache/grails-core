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
package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity

/**
 * Domain classes of the former {@code CollectionBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class CBNamedOwner {
    Long id
    static hasMany = [others: CBNamedInverse]
    static mapping = {
        others joinTable: [name: 'cb_named_join', schema: 'cb_schema', key: 'owner_ref', column: 'inverse_ref']
    }
}

@Entity
class CBNamedInverse {
    Long id
    static hasMany = [owners: CBNamedOwner]
    static belongsTo = CBNamedOwner
}

@Entity
class CBOwnNameOwner {
    Long id
    static hasMany = [others: CBOwnNameInverse]
    static mapping = {
        others joinTable: [name: 'cb_owner_name', schema: 'cb_schema']
    }
}

@Entity
class CBOwnNameInverse {
    Long id
    static hasMany = [owners: CBOwnNameOwner]
    static belongsTo = CBOwnNameOwner
    static mapping = {
        owners joinTable: [name: 'cb_inverse_own_name']
    }
}

@Entity
class Person {
    Long id
    String name
    static hasMany = [pets: Pet]
}

@Entity
class Pet {
    Long id
    String name
    static belongsTo = [owner: Person]
}

@Entity
class CBManyToManyA {
    Long id
    static hasMany = [others: CBManyToManyB]
}

@Entity
class CBManyToManyB {
    Long id
    static hasMany = [owners: CBManyToManyA]
    static belongsTo = CBManyToManyA
}
