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
import grails.gorm.transactions.Transactional
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
            CfkParent, CfkChild, CfkGrandParent, CfkMiddle, CfkLeaf)

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
