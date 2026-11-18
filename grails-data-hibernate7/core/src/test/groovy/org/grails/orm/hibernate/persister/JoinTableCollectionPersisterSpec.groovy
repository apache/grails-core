/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.persister

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.persister.collection.BasicCollectionPersister
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.persister.collection.OneToManyPersister
import org.hibernate.persister.internal.StandardPersisterClassResolver
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A set or a bag of entities in a join table deletes the row of a removed element by the owner and the element, so
 * the other elements stay (apache/grails-core#16691). GORM registers {@link GormPersisterClassResolver} unless the
 * application configures a resolver of its own.
 */
class JoinTableCollectionPersisterSpec extends Specification {

    HibernateDatastore datastore

    void cleanup() {
        datastore?.destroy()
    }

    @Unroll
    void 'the #association association has the persister #persister.simpleName'() {
        when:
        datastore = datastore('jtcpPersisters')

        then:
        persisterOf(owner, association).getClass() == persister

        where:
        owner      | association || persister
        JtcpItem   | 'roles'     || JoinTableCollectionPersister
        JtcpBag    | 'roles'     || JoinTableCollectionPersister
        JtcpUser   | 'groups'    || JoinTableCollectionPersister
        JtcpRanked | 'roles'     || BasicCollectionPersister
        JtcpOwner  | 'children'  || OneToManyPersister
    }

    void 'a persister class resolver the application configures is kept'() {
        when:
        datastore = datastore('jtcpCustom', ['hibernate.persister.resolver': StandardPersisterClassResolver.name])

        then:
        persisterOf(JtcpItem, 'roles').getClass() == BasicCollectionPersister
    }

    void 'an identifier bag and a soft-delete collection keep Hibernate persister'() {
        given:
        GormPersisterClassResolver resolver = new GormPersisterClassResolver()
        org.hibernate.mapping.ManyToOne element = Stub(org.hibernate.mapping.ManyToOne)

        when: 'an identifier bag of entities in a join table'
        org.hibernate.mapping.IdentifierBag idBag = new org.hibernate.mapping.IdentifierBag(null, null)
        idBag.element = element

        then:
        resolver.getCollectionPersisterClass(idBag) == BasicCollectionPersister

        when: 'a soft-delete collection of entities in a join table'
        org.hibernate.mapping.Set softDeleteSet = new org.hibernate.mapping.Set(null, null)
        softDeleteSet.element = element
        softDeleteSet.addAuxiliaryColumn(org.hibernate.mapping.SoftDeletable.INDICATOR, new org.hibernate.mapping.Column('deleted'))

        then:
        resolver.getCollectionPersisterClass(softDeleteSet) == BasicCollectionPersister

        when: 'an audit or temporal collection with an auxiliary table'
        org.hibernate.mapping.Set auxSet = new org.hibernate.mapping.Set(null, null)
        auxSet.element = element
        auxSet.auxiliaryTable = new org.hibernate.mapping.Table('audit_history')

        then:
        resolver.getCollectionPersisterClass(auxSet) == BasicCollectionPersister

        when: 'an ordinary set of entities in a join table'
        org.hibernate.mapping.Set ordinarySet = new org.hibernate.mapping.Set(null, null)
        ordinarySet.element = element

        then:
        resolver.getCollectionPersisterClass(ordinarySet) == JoinTableCollectionPersister
    }

    void 'removing one element of a set keeps the others'() {
        given: 'an item with three roles'
        datastore = datastore('jtcpSet')
        Long id = JtcpItem.withNewTransaction {
            JtcpItem item = new JtcpItem(name: 'item')
            ['a', 'b', 'c'].each { item.addToRoles(new JtcpRole(authority: it).save()) }
            item.save(flush: true).id
        }

        when: 'the roles change from a, b and c to c and d'
        JtcpItem.withNewTransaction {
            JtcpItem item = JtcpItem.get(id)
            item.roles.findAll { it.authority in ['a', 'b'] }.each { item.removeFromRoles(it) }
            item.addToRoles(new JtcpRole(authority: 'd').save())
            item.save(flush: true)
        }

        then:
        JtcpItem.withNewSession {
            JtcpItem.get(id).roles*.authority.sort() == ['c', 'd']
        }
    }

    void 'removing one element of a bag keeps the others'() {
        given:
        datastore = datastore('jtcpBag')
        Long id = JtcpBag.withNewTransaction {
            JtcpBag bag = new JtcpBag(name: 'bag')
            ['a', 'b'].each { bag.addToRoles(new JtcpRole(authority: it).save()) }
            bag.save(flush: true).id
        }

        when:
        JtcpBag.withNewTransaction {
            JtcpBag bag = JtcpBag.get(id)
            bag.removeFromRoles(bag.roles.find { it.authority == 'a' })
            bag.save(flush: true)
        }

        then:
        JtcpBag.withNewSession {
            JtcpBag.get(id).roles*.authority == ['b']
        }
    }

    void 'removing one element of a many-to-many keeps the others on both sides'() {
        given:
        datastore = datastore('jtcpManyToMany')
        Long id = JtcpUser.withNewTransaction {
            JtcpUser user = new JtcpUser(name: 'user')
            ['a', 'b'].each { user.addToGroups(new JtcpGroup(name: it)) }
            user.save(flush: true).id
        }

        when:
        JtcpUser.withNewTransaction {
            JtcpUser user = JtcpUser.get(id)
            user.removeFromGroups(user.groups.find { it.name == 'a' })
            user.save(flush: true)
        }

        then:
        JtcpUser.withNewSession {
            JtcpUser.get(id).groups*.name == ['b'] &&
                    JtcpGroup.findByName('b').users*.name == ['user'] &&
                    JtcpGroup.findByName('a').users.isEmpty()
        }
    }

    private static HibernateDatastore datastore(String database, Map<String, Object> extra = [:]) {
        Map<String, Object> settings = [
                'dataSource.url'     : "jdbc:h2:mem:${database};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
                'dataSource.dialect' : H2Dialect.name,
        ] + extra
        new HibernateDatastore(DatastoreUtils.createPropertyResolver(settings),
                JtcpItem, JtcpBag, JtcpRanked, JtcpRole, JtcpUser, JtcpGroup, JtcpOwner, JtcpChild)
    }

    private CollectionPersister persisterOf(Class owner, String association) {
        ((SessionFactoryImplementor) datastore.sessionFactory).mappingMetamodel
                .getCollectionDescriptor("${owner.name}.${association}".toString())
    }
}

@Entity
class JtcpItem {
    String name
    static hasMany = [roles: JtcpRole]
}

@Entity
class JtcpBag {
    String name
    Collection<JtcpRole> roles
    static hasMany = [roles: JtcpRole]
}

@Entity
class JtcpRanked {
    String name
    List<JtcpRole> roles
    static hasMany = [roles: JtcpRole]
}

@Entity
class JtcpRole {
    String authority
}

@Entity
class JtcpUser {
    String name
    static hasMany = [groups: JtcpGroup]
}

@Entity
class JtcpGroup {
    String name
    static hasMany = [users: JtcpUser]
    static belongsTo = JtcpUser
}

@Entity
class JtcpOwner {
    String name
    static hasMany = [children: JtcpChild]
}

@Entity
class JtcpChild {
    String name
    static belongsTo = [owner: JtcpOwner]
}
