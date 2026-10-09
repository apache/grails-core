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

import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification
import spock.lang.Unroll

/**
 * An application registers its entities in class-path scan order, so an entity is often bound before
 * the entity its composite foreign key references. The key must still be aligned with the referenced
 * identifier once that identifier is bound, exactly as when the referenced entity is registered first.
 * The entities are the ones of {@link CompositeForeignKeyColumnTypesSpec}, which registers every
 * referenced entity first.
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

    @Unroll
    void "the keys of a three level composite chain registered as #order are aligned with the keys they reference"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(config(classes), classes as Class[])

        when:
        Map<String, List<List<String>>> middleKeys = ForeignKeyPairs.of(datastore, 'PRB_MIDDLE')
        Map<String, List<List<String>>> leafKeys = ForeignKeyPairs.of(datastore, 'PRB_LEAF')

        then:
        middleKeys == [prb_grand: [['prb_grand_alpha', 'alpha'], ['prb_grand_zeta', 'zeta']]]
        leafKeys == [prb_middle: LEAF_KEY]

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

        cleanup:
        datastore?.close()

        where:
        order                  | classes
        'grand, leaf, middle'  | [PrbGrand, PrbLeaf, PrbMiddle]
        'middle, grand, leaf'  | [PrbMiddle, PrbGrand, PrbLeaf]
        'leaf, middle, grand'  | [PrbLeaf, PrbMiddle, PrbGrand]
    }

    @Unroll
    void "the keys that reference a hub registered as #order are aligned with the hub key"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(config(classes), classes as Class[])

        when:
        Map<String, List<List<String>>> refKeys = ForeignKeyPairs.of(datastore, 'PRB_HUB_REF')
        Map<String, List<List<String>>> joinTableKeys = ForeignKeyPairs.of(datastore, 'PRB_HUB_PRB_HUB_TAG')

        then:
        refKeys == [prb_hub: HUB_KEY]
        joinTableKeys == [prb_hub: HUB_KEY, prb_hub_tag: [['prb_hub_tag_id', 'id']]]

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

        cleanup:
        datastore?.close()

        where:
        order                       | classes
        'grand, hub, ref, tag'      | [PrbGrand, PrbHub, PrbHubRef, PrbHubTag]
        'ref, tag, hub, grand'      | [PrbHubRef, PrbHubTag, PrbHub, PrbGrand]
    }
}
