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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.NamingHelper
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.Table
import spock.lang.Specification
import spock.lang.Unroll

/**
 * An application registers its entities in class-path scan order, so an entity is often bound before
 * the entity its composite foreign key references. The key must still be aligned with the referenced
 * identifier once that identifier is bound, exactly as when the referenced entity is registered first.
 * The entities are the ones of {@link CompositeForeignKeyColumnTypesSpec}, which registers every
 * referenced entity first.
 * <p>
 * Each foreign key must also reach the metadata once. The inverse side of a bidirectional one-to-many
 * must not add a positional key of its own under the name of the key the to-one side creates: only the
 * first key of a name is created in the schema, and which one that is depends on the registration order.
 */
class CompositeForeignKeyRegistrationOrderSpec extends Specification {

    private static final List<List<String>> LEAF_KEY = [
            ['prb_middle_grand_parent_alpha', 'prb_grand_alpha'],
            ['prb_middle_grand_parent_zeta', 'prb_grand_zeta'],
            ['prb_middle_name', 'name']]

    private static final List<List<String>> HUB_KEY = [
            ['prb_hub_ace', 'ace'],
            ['prb_hub_grand_alpha', 'prb_grand_alpha'],
            ['prb_hub_grand_zeta', 'prb_grand_zeta'],
            ['prb_hub_zed', 'zed']]

    private static Map<String, Object> config(List<Class> classes) {
        [
                'dataSource.url'        : "jdbc:h2:mem:compositeFkOrder${classes*.simpleName.join('')};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'   : 'create-drop',
                'dataSource.dialect'    : H2Dialect.name,
                'hibernate.hbm2ddl.auto': 'create',
        ]
    }

    /**
     * The foreign key names that appear more than once in the Hibernate metadata, with their counts.
     * The schema export creates only the first key of a name and logs the others as failed commands.
     */
    private static Map<String, Integer> duplicateForeignKeyNames(HibernateDatastore datastore) {
        List<String> names = datastore.metadata.collectTableMappings().collectMany { Table table ->
            table.foreignKeyCollection*.name
        }
        names.countBy { it }.findAll { it.value > 1 }
    }

    @Unroll
    void "a leaf of a three level composite chain registered as #order is saved, reloaded and keyed by the referenced keys"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(config(classes), classes as Class[])

        when:
        PrbGrand.withNewTransaction {
            PrbGrand grand = new PrbGrand(zeta: 'z', alpha: 'a').save(failOnError: true)
            PrbMiddle middle = new PrbMiddle(name: 'm', grandParent: grand).save(failOnError: true)
            new PrbLeaf(name: 'l', middle: middle).save(failOnError: true, flush: true)
        }

        then:
        PrbLeaf.withNewSession {
            PrbLeaf leaf = PrbLeaf.findByName('l')
            leaf.middle.name == 'm' && leaf.middle.grandParent.alpha == 'a' && leaf.middle.grandParent.zeta == 'z'
        }

        when:
        Map<String, List<List<String>>> middleKeys = ForeignKeyPairs.of(datastore, 'PRB_MIDDLE')
        Map<String, List<List<String>>> leafKeys = ForeignKeyPairs.of(datastore, 'PRB_LEAF')

        then:
        middleKeys == [prb_grand: [['prb_grand_alpha', 'alpha'], ['prb_grand_zeta', 'zeta']]]
        leafKeys == [prb_middle: LEAF_KEY]

        and: 'the inverse collections add no key of their own under the names of these keys'
        duplicateForeignKeyNames(datastore) == [:]

        cleanup:
        datastore?.close()

        where:
        classes << [PrbGrand, PrbMiddle, PrbLeaf].permutations()
        order = classes*.simpleName.join(', ')
    }

    @Unroll
    void "the keys that reference a hub registered as #order are aligned with the hub key"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(config(classes), classes as Class[])

        when:
        PrbGrand.withNewTransaction {
            PrbGrand grand = new PrbGrand(zeta: 'z', alpha: 'a').save(failOnError: true)
            PrbHub hub = new PrbHub(zed: 'zz', ace: 'aa', grand: grand)
                    .addToTags(new PrbHubTag(label: 'tag'))
                    .save(failOnError: true)
            new PrbHubRef(name: 'ref', hub: hub).save(failOnError: true, flush: true)
        }

        then:
        PrbHubRef.withNewSession {
            PrbHubRef ref = PrbHubRef.findByName('ref')
            ref.hub.zed == 'zz' && ref.hub.ace == 'aa' && ref.hub.grand.zeta == 'z' && ref.hub.tags*.label == ['tag']
        }

        when:
        Map<String, List<List<String>>> refKeys = ForeignKeyPairs.of(datastore, 'PRB_HUB_REF')
        Map<String, List<List<String>>> joinTableKeys = ForeignKeyPairs.of(datastore, 'PRB_HUB_PRB_HUB_TAG')

        then:
        refKeys == [prb_hub: HUB_KEY]
        joinTableKeys == [prb_hub: HUB_KEY, prb_hub_tag: [['prb_hub_tag_id', 'id']]]
        duplicateForeignKeyNames(datastore) == [:]

        cleanup:
        datastore?.close()

        where:
        classes << [PrbGrand, PrbHub, PrbHubRef, PrbHubTag].permutations()
        order = classes*.simpleName.join(', ')
    }

    @Unroll
    void "the child of a bidirectional one-to-many into a hub registered as #order is saved, reloaded and keyed by the hub key"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(config(classes), classes as Class[])

        when:
        PrbGrand.withNewTransaction {
            PrbGrand grand = new PrbGrand(zeta: 'z', alpha: 'a').save(failOnError: true)
            new PrbHub(zed: 'zz', ace: 'aa', grand: grand)
                    .addToChildren(new PrbHubChild(name: 'kid'))
                    .save(failOnError: true, flush: true)
        }

        then:
        PrbHubChild.withNewSession {
            PrbHubChild child = PrbHubChild.findByName('kid')
            child.hub.zed == 'zz' && child.hub.ace == 'aa' && child.hub.grand.alpha == 'a'
        }

        when:
        Map<String, List<List<String>>> childKeys = ForeignKeyPairs.of(datastore, 'PRB_HUB_CHILD')

        then:
        childKeys == [prb_hub: HUB_KEY]

        and: 'the inverse collection adds no key of its own under the name of the child key'
        duplicateForeignKeyNames(datastore) == [:]

        cleanup:
        datastore?.close()

        where:
        classes << [PrbGrand, PrbHub, PrbHubChild].permutations()
        order = classes*.simpleName.join(', ')
    }

    void "the inverse one-to-many of an entity with a simple identifier keeps the one implicitly named key of its to-one side"() {
        given:
        List<Class> classes = [PrbPlainKid, PrbPlainParent]
        HibernateDatastore datastore = new HibernateDatastore(config(classes), classes as Class[])

        when:
        PrbPlainParent.withNewTransaction {
            new PrbPlainParent(name: 'p').addToKids(new PrbPlainKid(name: 'k')).save(failOnError: true, flush: true)
        }

        then:
        PrbPlainParent.withNewSession {
            PrbPlainParent.findByName('p').kids*.name == ['k']
        }

        when:
        Table kidTable = datastore.metadata.collectTableMappings().find { Table table -> table.name == 'prb_plain_kid' }

        then: 'the key keeps the columns and the implicit name an existing schema has for it'
        ForeignKeyPairs.of(datastore, 'PRB_PLAIN_KID') == [prb_plain_parent: [['parent_id', 'id']]]
        kidTable.foreignKeyCollection*.name == [NamingHelper.INSTANCE.generateHashedFkName(
                'FK', Identifier.toIdentifier('prb_plain_kid'), Identifier.toIdentifier('prb_plain_parent'),
                Identifier.toIdentifier('parent_id'))]
        duplicateForeignKeyNames(datastore) == [:]

        cleanup:
        datastore?.close()
    }
}

@Entity
class PrbPlainParent {
    String name
    static hasMany = [kids: PrbPlainKid]
}

@Entity
class PrbPlainKid {
    String name
    static belongsTo = [parent: PrbPlainParent]
}
