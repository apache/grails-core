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

import java.sql.Connection

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A foreign key to an entity whose composite identifier has parts of different types must name
 * and type each column after the referenced primary key column it points at.
 */
class CompositeForeignKeyColumnTypesSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(
            [
                    'dataSource.url'      : 'jdbc:h2:mem:compositeFkTypesDB;LOCK_TIMEOUT=10000',
                    'dataSource.dbCreate' : 'create-drop',
                    'dataSource.dialect'  : H2Dialect.name,
                    'hibernate.hbm2ddl.auto': 'create',
            ],
            CfkParent, CfkChild, CfkGrandParent, CfkMiddle, CfkLeaf,
            CfkOrdParent, CfkOrdChild, CfkOrdGrand, CfkOrdMiddle, CfkOrdLeaf,
            PrbGrand, PrbMiddle, PrbLeaf, PrbHub, PrbHubTag, PrbHubRef)

    private List<List> columns(String table) {
        List<List> rows = []
        datastore.sessionFactory.openSession().withCloseable { session ->
            session.doWork { Connection c ->
                c.createStatement().withCloseable { st ->
                    st.executeQuery(
                            "select column_name, data_type from information_schema.columns where table_name = '${table}' order by ordinal_position".toString())
                            .withCloseable { rs ->
                                while (rs.next()) {
                                    rows << [rs.getString(1).toLowerCase(), rs.getString(2).toUpperCase()]
                                }
                            }
                }
            }
        }
        rows
    }


    /**
     * The foreign keys of a table, each as its column pairs [foreign key column, referenced column] in
     * {@code KEY_SEQ} order, which is the order the database matches the columns of a key by.
     */
    private Map<String, List<List<String>>> foreignKeyPairs(String table) {
        Map<String, List<List<String>>> keys = [:]
        datastore.sessionFactory.openSession().withCloseable { session ->
            session.doWork { Connection c ->
                c.metaData.getImportedKeys(null, null, table).withCloseable { rs ->
                    List<List> rows = []
                    while (rs.next()) {
                        rows << [rs.getString('FK_NAME'), rs.getInt('KEY_SEQ'),
                                 rs.getString('PKTABLE_NAME').toLowerCase(),
                                 rs.getString('FKCOLUMN_NAME').toLowerCase(), rs.getString('PKCOLUMN_NAME').toLowerCase()]
                    }
                    rows.sort { it[1] }.each { List row ->
                        keys.get(row[0] + ' -> ' + row[2], []) << [row[3], row[4]]
                    }
                }
            }
        }
        keys.collectEntries { String name, List<List<String>> pairs -> [(name.substring(name.indexOf(' -> ') + 4)): pairs] }
    }

    void "a foreign key to a composite parent pairs its columns with the referenced key columns in key order"() {
        expect:
        foreignKeyPairs('CFK_CHILD') == [
                cfk_parent: [['cfk_parent_lucky_number', 'lucky_number'], ['cfk_parent_name', 'name']]
        ]
    }

    void "a foreign key to a composite parent whose parts are declared out of name order pairs the same-typed parts in key order"() {
        expect: 'the parts are declared as zeta, alpha, and the primary key and the foreign key both follow the name order'
        foreignKeyPairs('CFK_ORD_CHILD') == [
                cfk_ord_parent: [['cfk_ord_parent_alpha', 'alpha'], ['cfk_ord_parent_zeta', 'zeta']]
        ]
    }

    void "the foreign keys of a three level composite chain pair their columns with the referenced key columns in key order"() {
        expect:
        foreignKeyPairs('CFK_MIDDLE') == [
                cfk_grand_parent: [['cfk_grand_parent_lucky_number', 'lucky_number'], ['cfk_grand_parent_name', 'name']]
        ]
        foreignKeyPairs('CFK_LEAF') == [
                cfk_middle: [
                        ['cfk_middle_grand_parent_lucky_number', 'cfk_grand_parent_lucky_number'],
                        ['cfk_middle_grand_parent_name', 'cfk_grand_parent_name'],
                        ['cfk_middle_name', 'name']]
        ]
    }

    void "the foreign keys of a three level chain with out of order, same-typed parts pair them in key order"() {
        expect: 'the nested composite is declared as zeta, alpha, so only its order tells the columns apart'
        foreignKeyPairs('CFK_ORD_MIDDLE') == [
                cfk_ord_grand: [['cfk_ord_grand_alpha', 'alpha'], ['cfk_ord_grand_zeta', 'zeta']]
        ]
        foreignKeyPairs('CFK_ORD_LEAF') == [
                cfk_ord_middle: [
                        ['cfk_ord_middle_grand_parent_alpha', 'cfk_ord_grand_alpha'],
                        ['cfk_ord_middle_grand_parent_zeta', 'cfk_ord_grand_zeta'],
                        ['cfk_ord_middle_name', 'name']]
        ]
    }

    void "foreign key columns carry the type of the primary key column they are named after"() {
        when:
        Map<String, String> parent = columns('CFK_PARENT').collectEntries { [(it[0]): it[1]] }
        Map<String, String> child = columns('CFK_CHILD').collectEntries { [(it[0]): it[1]] }

        then:
        parent.name == 'CHARACTER VARYING'
        parent.lucky_number == 'INTEGER'
        child.cfk_parent_name == parent.name
        child.cfk_parent_lucky_number == parent.lucky_number
    }

    void "foreign key columns of a three level composite chain are named and typed after the referenced key"() {
        when:
        Map<String, String> grand = columns('CFK_GRAND_PARENT').collectEntries { [(it[0]): it[1]] }
        Map<String, String> middle = columns('CFK_MIDDLE').collectEntries { [(it[0]): it[1]] }

        Map<String, String> leaf = columns('CFK_LEAF').collectEntries { [(it[0]): it[1]] }

        then:
        grand.name
        middle.cfk_grand_parent_name == grand.name
        middle.cfk_grand_parent_lucky_number == grand.lucky_number
        leaf.cfk_middle_grand_parent_name == grand.name
        leaf.cfk_middle_grand_parent_lucky_number == grand.lucky_number
    }

    void "a leaf of a three level composite chain is saved, reloaded and found through the association"() {
        when:
        CfkGrandParent.withNewTransaction {
            CfkGrandParent grand = new CfkGrandParent(name: 'Fred', luckyNumber: 7).save(failOnError: true)
            CfkMiddle middle = new CfkMiddle(name: 'Bob', grandParent: grand).save(failOnError: true)
            new CfkLeaf(name: 'Chuck', middle: middle).save(failOnError: true, flush: true)
        }

        then:
        CfkLeaf.withNewSession {
            CfkLeaf leaf = CfkLeaf.findByName('Chuck')
            leaf.middle.name == 'Bob' && leaf.middle.grandParent.luckyNumber == 7
        }
    }

    void "a child referencing a composite parent is saved, reloaded and found through the association"() {
        when:
        CfkParent.withNewTransaction {
            CfkParent parent = new CfkParent(name: 'Fred', luckyNumber: 7).save(failOnError: true)
            new CfkChild(label: 'kid', parent: parent).save(failOnError: true, flush: true)
        }

        then:
        CfkChild.withNewSession {
            CfkChild child = CfkChild.findByLabel('kid')
            child.parent.name == 'Fred' && child.parent.luckyNumber == 7
        }
        CfkChild.withNewSession {
            CfkChild.where { parent.name == 'Fred' && parent.luckyNumber == 7 }.count() == 1
        }
    }

    void "a nested composite part that sorts after a plain part moves with all of its columns"() {
        expect: 'the middle key is declared as name, grandParent, so grandParent spans the columns that sort before and after name'
        foreignKeyPairs('PRB_MIDDLE') == [
                prb_grand: [['prb_grand_alpha', 'alpha'], ['prb_grand_zeta', 'zeta']]
        ]
        foreignKeyPairs('PRB_LEAF') == [
                prb_middle: [
                        ['prb_middle_grand_parent_alpha', 'prb_grand_alpha'],
                        ['prb_middle_grand_parent_zeta', 'prb_grand_zeta'],
                        ['prb_middle_name', 'name']]
        ]
    }

    void "a leaf whose composite key nests a part declared after a plain part is saved and reloaded"() {
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
    }

    void "the keys that reference a multi-column nested part between two plain parts move it with all of its columns"() {
        expect: 'the hub key is declared as zed, grand, ace, so the two column grand part sorts between the two plain parts'
        foreignKeyPairs('PRB_HUB_REF') == [
                prb_hub: [
                        ['prb_hub_ace', 'ace'],
                        ['prb_hub_grand_alpha', 'prb_grand_alpha'],
                        ['prb_hub_grand_zeta', 'prb_grand_zeta'],
                        ['prb_hub_zed', 'zed']]
        ]

        and: 'the join table of the unidirectional one-to-many references the hub by the same key'
        foreignKeyPairs('PRB_HUB_PRB_HUB_TAG') == [
                prb_hub: [
                        ['prb_hub_ace', 'ace'],
                        ['prb_hub_grand_alpha', 'prb_grand_alpha'],
                        ['prb_hub_grand_zeta', 'prb_grand_zeta'],
                        ['prb_hub_zed', 'zed']],
                prb_hub_tag: [['prb_hub_tag_id', 'id']]
        ]
    }

    void "a hub whose multi-column nested part sorts between two plain parts is saved with a tag and a reference and reloaded"() {
        when:
        PrbGrand.withNewTransaction {
            PrbGrand grand = new PrbGrand(zeta: 'z2', alpha: 'a2').save(failOnError: true)
            PrbHub hub = new PrbHub(zed: 'zz', ace: 'aa', grand: grand)
                    .addToTags(new PrbHubTag(label: 'tag'))
                    .save(failOnError: true)
            new PrbHubRef(name: 'ref', hub: hub).save(failOnError: true, flush: true)
        }

        then:
        PrbHubRef.withNewSession {
            PrbHubRef ref = PrbHubRef.findByName('ref')
            ref.hub.zed == 'zz' && ref.hub.ace == 'aa' && ref.hub.grand.zeta == 'z2' && ref.hub.tags*.label == ['tag']
        }
    }
}

@Entity
class PrbGrand implements Serializable {
    String zeta
    String alpha
    static hasMany = [middles: PrbMiddle]

    static mapping = MappingBuilder.define {
        composite('zeta', 'alpha')
    }
}

@Entity
class PrbMiddle implements Serializable {
    String name
    static belongsTo = [grandParent: PrbGrand]
    static hasMany = [leaves: PrbLeaf]

    static mapping = MappingBuilder.define {
        composite('name', 'grandParent')
    }
}

@Entity
class PrbLeaf implements Serializable {
    String name
    static belongsTo = [middle: PrbMiddle]

    static mapping = MappingBuilder.define {
        composite('middle', 'name')
    }
}

@Entity
class PrbHub implements Serializable {
    String zed
    String ace
    PrbGrand grand
    static hasMany = [tags: PrbHubTag]

    static mapping = MappingBuilder.define {
        composite('zed', 'grand', 'ace')
    }
}

@Entity
class PrbHubTag {
    String label
}

@Entity
class PrbHubRef {
    String name
    PrbHub hub
}

@Entity
class CfkParent implements Serializable {
    String name
    Integer luckyNumber

    static mapping = MappingBuilder.define {
        composite('name', 'luckyNumber')
    }
}

@Entity
class CfkChild implements Serializable {
    String label
    CfkParent parent

    static mapping = MappingBuilder.define {
        composite('parent', 'label')
    }
}

@Entity
class CfkLeaf implements Serializable {
    String name
    static belongsTo = [middle: CfkMiddle]

    static mapping = MappingBuilder.define {
        composite('middle', 'name')
    }
}

@Entity
class CfkMiddle implements Serializable {
    String name
    static belongsTo = [grandParent: CfkGrandParent]
    static hasMany = [leaves: CfkLeaf]

    static mapping = MappingBuilder.define {
        composite('grandParent', 'name')
    }
}

@Entity
class CfkGrandParent implements Serializable {
    String name
    Integer luckyNumber
    static hasMany = [middles: CfkMiddle]

    static mapping = MappingBuilder.define {
        composite('name', 'luckyNumber')
    }
}

@Entity
class CfkOrdParent implements Serializable {
    String zeta
    String alpha

    static mapping = MappingBuilder.define {
        composite('zeta', 'alpha')
    }
}

@Entity
class CfkOrdChild implements Serializable {
    String label
    CfkOrdParent parent

    static mapping = MappingBuilder.define {
        composite('parent', 'label')
    }
}

@Entity
class CfkOrdLeaf implements Serializable {
    String name
    static belongsTo = [middle: CfkOrdMiddle]

    static mapping = MappingBuilder.define {
        composite('middle', 'name')
    }
}

@Entity
class CfkOrdMiddle implements Serializable {
    String name
    static belongsTo = [grandParent: CfkOrdGrand]
    static hasMany = [leaves: CfkOrdLeaf]

    static mapping = MappingBuilder.define {
        composite('grandParent', 'name')
    }
}

@Entity
class CfkOrdGrand implements Serializable {
    String zeta
    String alpha
    static hasMany = [middles: CfkOrdMiddle]

    static mapping = MappingBuilder.define {
        composite('zeta', 'alpha')
    }
}
