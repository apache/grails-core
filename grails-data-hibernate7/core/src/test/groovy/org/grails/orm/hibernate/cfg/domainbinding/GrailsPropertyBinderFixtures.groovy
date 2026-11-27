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
 * Domain classes of the former {@code GrailsPropertyBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class PropertyBinderSpecSimpleBook {
    Long id
    String title
}

@Entity
class PropertyBinderSpecEnumBook {
    Long id
    java.util.concurrent.TimeUnit status
}

@Entity
class PropertyBinderSpecEnumCollection {
    Long id
    Set<java.util.concurrent.TimeUnit> statuses
    static hasMany = [statuses: java.util.concurrent.TimeUnit]
}

@Entity
class PropertyBinderSpecAuthor {
    Long id
    static hasMany = [pets: PropertyBinderSpecPet]
}

@Entity
class PropertyBinderSpecPet {
    Long id
    PropertyBinderSpecAuthor owner
}

@Entity
class PropertyBinderSpecEmployee {
    Long id
    PropertyBinderSpecAddress address
    static embedded = ['address']
}

class PropertyBinderSpecAddress implements Serializable {
    String city
}

@Entity
class PropertyBinderSpecSerializableEntity {
    Long id
    List<String> tags
    static mapping = {
        tags type: 'serializable'
    }
}

@Entity
class PropertyBinderSpecCustomEntity {
    Long id
    String data
    static mapping = {
        data type: 'org.hibernate.type.YesNoConverter'
    }
}

@Entity
class PropertyBinderSpecCustomUserTypeCollection {
    Long id
    Set<String> categories
    static mapping = {
        categories type: 'org.hibernate.type.YesNoConverter' 
    }
}

@Entity
class PropertyBinderSpecHasOneProfile {
    Long id
    String bio
    PropertyBinderSpecHasOneOwner owner
    static belongsTo = [owner: PropertyBinderSpecHasOneOwner]
}

@Entity
class PropertyBinderSpecHasOneOwner {
    Long id
    static hasOne = [profile: PropertyBinderSpecHasOneProfile]
}

@Entity
class PropertyBinderSpecFKChild {
    Long id
    PropertyBinderSpecFKOwner owner
    static belongsTo = [owner: PropertyBinderSpecFKOwner]
}

@Entity
class PropertyBinderSpecFKOwner {
    Long id
    PropertyBinderSpecFKChild child
}

@Entity
class PropertyBinderSpecTenantEntity implements grails.gorm.MultiTenant<PropertyBinderSpecTenantEntity> {
    Long id
    Integer tenantId
}
