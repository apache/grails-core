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
package org.grails.orm.hibernate

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.orm.hibernate.cfg.Settings

import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/apache/grails-core/issues/14345')
class SchemaTenantAssociationSpec extends Specification {

    private static HibernateDatastore start(Class... classes) {
        Map config = [
            (Settings.SETTING_DB_CREATE)                 : 'create-drop',
            'dataSource.url'                             : "jdbc:h2:mem:schemaTenantAssociation${System.nanoTime()};LOCK_TIMEOUT=10000",
            'grails.gorm.multiTenancy.mode'              : 'SCHEMA',
            'grails.gorm.multiTenancy.tenantResolverClass': 'org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver'
        ]
        new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), classes)
    }

    void "a hasMany from a multi tenant entity to an entity that is not multi tenant is refused with a clear message"() {
        when:
        start(StaOwner, StaShared)

        then:
        ConfigurationException e = thrown()
        e.message.contains('StaOwner.shared -> StaShared')
        e.message.contains('DISCRIMINATOR')
    }

    void "a to-one association from a multi tenant entity to an entity that is not multi tenant is refused"() {
        when:
        start(StaRef, StaShared)

        then:
        ConfigurationException e = thrown()
        e.message.contains('StaRef.shared -> StaShared')
    }

    void "associations between multi tenant entities still start"() {
        when:
        HibernateDatastore datastore = start(StaTenantChild, StaTenantParent)

        then:
        noExceptionThrown()

        cleanup:
        datastore?.close()
    }
}

@Entity
class StaShared {
    String name
}

@Entity
class StaOwner implements MultiTenant<StaOwner> {
    String name
    static hasMany = [shared: StaShared]
}

@Entity
class StaRef implements MultiTenant<StaRef> {
    String name
    StaShared shared
}

@Entity
class StaTenantChild implements MultiTenant<StaTenantChild> {
    String name
}

@Entity
class StaTenantParent implements MultiTenant<StaTenantParent> {
    String name
    StaTenantChild child
    static hasMany = [children: StaTenantChild]
}
