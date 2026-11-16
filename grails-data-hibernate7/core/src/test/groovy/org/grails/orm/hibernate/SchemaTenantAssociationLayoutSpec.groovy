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
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * With SCHEMA multi-tenancy a tenant schema maps the multi tenant entities and the others only when they use every
 * connection. The startup check must refuse exactly the associations that have nothing to refer to, so each layout
 * a mapping can declare for the target is tried: the ones that reach the tenant schemas must start.
 */
@Issue('https://github.com/apache/grails-core/issues/14345')
class SchemaTenantAssociationLayoutSpec extends Specification {

    private static HibernateDatastore start(Class... classes) {
        startWith([:], classes)
    }

    private static HibernateDatastore startWith(Map extra, Class... classes) {
        Map config = [
            (Settings.SETTING_DB_CREATE)                 : 'create-drop',
            'dataSource.url'                             : "jdbc:h2:mem:schemaTenantLayout${System.nanoTime()};LOCK_TIMEOUT=10000",
            'grails.gorm.multiTenancy.mode'              : 'SCHEMA',
            'grails.gorm.multiTenancy.tenantResolverClass': 'org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver'
        ]
        config.putAll(extra)
        new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), classes)
    }

    @Unroll
    void "an owner of a multi tenant entity pointing at a target #layout is refused at startup"() {
        when:
        start(*classes)

        then:
        ConfigurationException e = thrown()
        e.message.contains(classes[0].simpleName)

        where:
        layout                          | classes
        'with no datasource declared'   | [LayoutOwnerNone, LayoutTargetNone]
    }

    @Unroll
    void "an owner of a multi tenant entity pointing at a target #layout starts"() {
        when:
        HibernateDatastore datastore = start(*classes)

        then:
        noExceptionThrown()

        cleanup:
        datastore?.close()

        where:
        layout                                                   | classes
        'that declares datasource ALL'                           | [LayoutOwnerAll, LayoutTargetAll]
        'that declares datasources with only ALL'                | [LayoutOwnerAllList, LayoutTargetAllList]
        'that is a subclass of a root using every connection'    | [LayoutOwnerChild, LayoutAllRoot, LayoutAllChild]
        'that is the root of a hierarchy using every connection' | [LayoutOwnerRoot, LayoutAllRoot, LayoutAllChild]
        'that is mapped by a JPA annotation and lists no datasource' | [LayoutOwnerJpa, LayoutJpaTarget]
    }

    private static HibernateDatastore startWithTenants(Class... classes) {
        startWith(['grails.gorm.multiTenancy.tenantResolverClass': KnownTenantsResolver.name], classes)
    }

    @Unroll
    void "with the tenant schemas known, an owner pointing at a target that #layout starts"() {
        when:
        HibernateDatastore datastore = startWithTenants(*classes)

        then:
        noExceptionThrown()

        cleanup:
        datastore?.close()

        where:
        layout                                          | classes
        'lists the default connection and the tenant'   | [KnownOwnerDefaultAndTenant, KnownTargetDefaultAndTenant]
        'declares datasource ALL'                       | [KnownOwnerAll, KnownTargetAll]
        'is mapped by a JPA annotation and lists no datasource' | [KnownOwnerJpa, KnownJpaTarget]
    }

    @Unroll
    void "with the tenant schemas known, an owner pointing at a target that #layout is refused at startup"() {
        when:
        startWithTenants(*classes)

        then:
        ConfigurationException e = thrown()
        e.message.contains(classes[0].simpleName)

        where:
        layout                                                  | classes
        'lists only the default connection'                     | [KnownOwnerDefaultOnly, KnownTargetDefaultOnly]
        'lists the default connection and another tenant'       | [KnownOwnerOtherTenant, KnownTargetOtherTenant]
    }

    void "with the tenant schemas known, an owner pointing at a target that lists only the tenant schema does not start"() {
        when: 'the default connection also maps the owner, and not the target; Hibernate refuses it before the schema check runs'
        startWithTenants(KnownOwnerTenantOnly, KnownTargetTenantOnly)

        then:
        thrown(Exception)
    }

    void "an owner of a multi tenant entity pointing at a target that uses every connection through the default mapping starts"() {
        when:
        HibernateDatastore datastore = startWith(['grails.gorm.default.mapping': { datasource 'ALL' }],
                LayoutOwnerDefault, LayoutTargetDefault)

        then:
        noExceptionThrown()

        cleanup:
        datastore?.close()
    }
}

@Entity
class LayoutTargetNone {
    String name
}

@Entity
class LayoutOwnerNone implements MultiTenant<LayoutOwnerNone> {
    String name
    LayoutTargetNone target
}

@Entity
class LayoutTargetAll {
    String name
    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class LayoutOwnerAll implements MultiTenant<LayoutOwnerAll> {
    String name
    LayoutTargetAll target
}

@Entity
class LayoutTargetAllList {
    String name
    static mapping = {
        datasources(['ALL'])
    }
}

@Entity
class LayoutOwnerAllList implements MultiTenant<LayoutOwnerAllList> {
    String name
    LayoutTargetAllList target
}

@Entity
class LayoutAllRoot {
    String name
    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class LayoutAllChild extends LayoutAllRoot {
    String extra
}

@Entity
class LayoutOwnerChild implements MultiTenant<LayoutOwnerChild> {
    String name
    LayoutAllChild target
}

@Entity
class LayoutOwnerRoot implements MultiTenant<LayoutOwnerRoot> {
    String name
    LayoutAllRoot target
}

@Entity
class LayoutTargetDefault {
    String name
}

@Entity
class LayoutOwnerDefault implements MultiTenant<LayoutOwnerDefault> {
    String name
    LayoutTargetDefault target
}

@jakarta.persistence.Entity
class LayoutJpaTarget {
    @jakarta.persistence.Id
    Long id
    String name
}

@Entity
class LayoutOwnerJpa implements MultiTenant<LayoutOwnerJpa> {
    String name
    LayoutJpaTarget target
}

@jakarta.persistence.Entity
class KnownJpaTarget {
    @jakarta.persistence.Id
    Long id
    String name
}

@Entity
class KnownOwnerJpa implements MultiTenant<KnownOwnerJpa> {
    String name
    KnownJpaTarget target
}

class KnownTenantsResolver implements AllTenantsResolver {
    @Override
    Serializable resolveTenantIdentifier() {
        'tenant1'
    }

    @Override
    Iterable<Serializable> resolveTenantIds() {
        ['tenant1']
    }
}

@Entity
class KnownTargetDefaultAndTenant {
    String name
    static mapping = {
        datasources(['default', 'tenant1'])
    }
}

@Entity
class KnownOwnerDefaultAndTenant implements MultiTenant<KnownOwnerDefaultAndTenant> {
    String name
    KnownTargetDefaultAndTenant target
}

@Entity
class KnownTargetTenantOnly {
    String name
    static mapping = {
        datasources(['tenant1'])
    }
}

@Entity
class KnownOwnerTenantOnly implements MultiTenant<KnownOwnerTenantOnly> {
    String name
    KnownTargetTenantOnly target
}

@Entity
class KnownTargetAll {
    String name
    static mapping = {
        datasource 'ALL'
    }
}

@Entity
class KnownOwnerAll implements MultiTenant<KnownOwnerAll> {
    String name
    KnownTargetAll target
}

@Entity
class KnownTargetDefaultOnly {
    String name
}

@Entity
class KnownOwnerDefaultOnly implements MultiTenant<KnownOwnerDefaultOnly> {
    String name
    KnownTargetDefaultOnly target
}

@Entity
class KnownTargetOtherTenant {
    String name
    static mapping = {
        datasources(['default', 'tenant2'])
    }
}

@Entity
class KnownOwnerOtherTenant implements MultiTenant<KnownOwnerOtherTenant> {
    String name
    KnownTargetOtherTenant target
}
