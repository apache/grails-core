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
package grails.gorm.specs.compositeid

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import jakarta.annotation.Nonnull
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Issue

/**
 * Created by graemerocher on 17/02/2017.
 */
//TODO 2025-04-17 CompositeId not working
class GlobalConstraintWithCompositeIdSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.domainClasses.addAll([ParentB, ChildB, DomainB])
        manager.grailsConfig = [
                'dataSource.url'                 : "jdbc:tc:postgresql:latest:///dev_db",
                'dataSource.dbCreate'            : 'create-drop',
                'dataSource.formatSql'           : 'true',
                'dataSource.logSql'              : 'true',
                'hibernate.flush.mode'           : 'COMMIT',
                'hibernate.cache.queries'        : 'true',
                'hibernate.hbm2ddl.auto'         : 'create',
                'hibernate.type.descriptor.sql'  : 'true',
                'grails.gorm.default.constraints': {
                    '*'(nullable: true)
                }
        ]
    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10457')
    void "test global constraints with composite id"() {
        when:
        ParentB parent = new ParentB(code: "AAA", desc: "BBB")
                .addToChildren(name: "Child A")
                .save(flush: true)

        then:
        ParentB.count == 1
        ChildB.count == 1
    }

//    @Ignore("DDL not working for composite id")
    @Issue('https://github.com/grails/grails-data-mapping/issues/877')
    void "test global constraints with unique constraint"() {
        given:
        PersistentEntity entity = manager.hibernateDatastore.mappingContext.getPersistentEntity(DomainB.name)
        PropertyConfig nameProp = entity.getPropertyByName('name').mapping.mappedForm
        PropertyConfig someOtherConfig = entity.getPropertyByName('someOther').mapping.mappedForm
        expect:
        nameProp.unique
        someOtherConfig.unique
        !nameProp.uniquenessGroup.isEmpty()
        nameProp.uniquenessGroup.contains('domainB')
        someOtherConfig.uniquenessGroup.isEmpty()

    }
}


@Entity
class ParentB implements Serializable {

    String code
    String desc
    SortedSet<ChildB> children

    static hasMany = [children: ChildB]

    static constraints = {
    }

    static mapping = {
        id composite: ['code', 'desc']

        code column: 'COD'
        desc column: 'DSC'
    }
}

@Entity
class ChildB implements Serializable, Comparable<ChildB> {
    String name

    static belongsTo = [parent: ParentB]

    static constraints = {
    }

    static mapping = {
        id composite: ['name', 'parent']

        columns {
            parent {
                column name: 'COD'
                column name: 'DSC'
            }
        }
    }

    @Override
    int compareTo(@Nonnull ChildB o) {
        this.name <=> o.name
    }
}

@Entity
class DomainB {

    String name

    String someOther

    static belongsTo = [domainB: DomainB]

    static constraints = {
        name nullable: false, blank: false, unique: "domainB"
        someOther nullable: false, blank: false, unique: true
    }
}
