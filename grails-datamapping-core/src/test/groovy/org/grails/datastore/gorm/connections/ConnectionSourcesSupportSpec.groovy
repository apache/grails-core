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
package org.grails.datastore.gorm.connections

import grails.gorm.MultiTenant
import org.grails.datastore.mapping.core.connections.ConnectionSourcesSupport
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/apache/grails-core/issues/14345')
class ConnectionSourcesSupportSpec extends Specification {

    private static List<String> findIn(Class... classes) {
        SimpleMapDatastore datastore = new SimpleMapDatastore(classes)
        try {
            ConnectionSourcesSupport.findAssociationsToEntitiesMissingFromTenantSchemas(datastore.mappingContext.persistentEntities)
        } finally {
            datastore.close()
        }
    }

    void "a hasMany from a multi tenant entity to an entity that is not multi tenant is reported"() {
        expect:
        findIn(SharedThing, TenantOwner) == ['TenantOwner.things -> SharedThing']
    }

    void "a to-one association from a multi tenant entity to an entity that is not multi tenant is reported"() {
        expect:
        findIn(SharedThing, TenantRef) == ['TenantRef.shared -> SharedThing']
    }

    void "associations between multi tenant entities are not reported"() {
        expect:
        findIn(TenantChild, TenantOwnerOfTenants).isEmpty()
    }

    void "an association from an entity that is not multi tenant is not reported"() {
        expect:
        findIn(SharedThing, PlainOwner).isEmpty()
    }
}

@Entity
class SharedThing {
    String name
}

@Entity
class TenantOwner implements MultiTenant<TenantOwner> {
    String name
    static hasMany = [things: SharedThing]
}

@Entity
class TenantRef implements MultiTenant<TenantRef> {
    String name
    SharedThing shared
}

@Entity
class TenantChild implements MultiTenant<TenantChild> {
    String name
}

@Entity
class TenantOwnerOfTenants implements MultiTenant<TenantOwnerOfTenants> {
    String name
    TenantChild child
    static hasMany = [children: TenantChild]
}

@Entity
class PlainOwner {
    String name
    static hasMany = [things: SharedThing]
}
