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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.mapping.Collection as HibernateCollection
import org.hibernate.mapping.Table
import spock.lang.PendingFeature

/**
 * Pins defects of the domain binder found while auditing which mapping options {@link GrailsDomainGenerator} drops. The
 * binder silently ignores the options below; the generator either states what the mapping asks for (and the differential spec
 * lists the difference) or rejects the mapping by name. Each feature reports as fixed when the binder is.
 */
class GrailsDomainBinderOptionDefectSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(DefectWritable, DefectReadOnlyColumns, DefectEnumGroup, DefectCollectionGroup)
    }

    void "a column with no write restriction is inserted and updated"() {
        given:
        DefectWritable row = new DefectWritable(inserted: 'a', updated: 'b').save(flush: true, failOnError: true)
        session.clear()
        DefectWritable loaded = DefectWritable.get(row.id)
        loaded.updated = 'c'
        loaded.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectWritable.get(row.id).inserted == 'a'
        DefectWritable.get(row.id).updated == 'c'
    }

    @PendingFeature(reason = 'PropertyBinder overwrites the insertable flag of the property with the one of its columns, which are always insertable, so insertable: false is ignored')
    void "insertable false keeps the value out of the insert"() {
        given:
        DefectReadOnlyColumns row = new DefectReadOnlyColumns(inserted: 'a', updated: 'b').save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectReadOnlyColumns.get(row.id).inserted == null
    }

    @PendingFeature(reason = 'PropertyBinder overwrites the updatable flag of the property with the one of its columns, which are always updatable, so updatable: false is ignored')
    void "updatable false keeps the value out of the update"() {
        given:
        DefectReadOnlyColumns row = new DefectReadOnlyColumns(updated: 'b').save(flush: true, failOnError: true)
        session.clear()
        DefectReadOnlyColumns loaded = DefectReadOnlyColumns.get(row.id)
        loaded.updated = 'c'
        loaded.save(flush: true, failOnError: true)
        session.clear()

        expect:
        DefectReadOnlyColumns.get(row.id).updated == 'b'
    }

    @PendingFeature(reason = 'EnumTypeBinder never calls CreateKeyForProps, so a unique group on an enum property makes no unique key')
    void "a unique group on an enum property is unique in the database"() {
        given:
        new DefectEnumGroup(state: DefectState.ON, other: 'a').save(flush: true, failOnError: true)
        session.clear()

        when:
        new DefectEnumGroup(state: DefectState.ON, other: 'a').save(flush: true, validate: false)

        then:
        thrown(Exception)
    }

    @PendingFeature(reason = 'CollectionKeyBinder runs ColumnBinder on the collection property, so a unique group becomes a unique key of the collection table that names a column of the owner\'s table')
    void "the unique key of a collection table names only columns of that table"() {
        given:
        Table table = ((HibernateCollection) getPersistentEntity(DefectCollectionGroup).persistentClass.getProperty('tags').value).collectionTable
        Set<String> columns = table.columns*.name.toSet()

        expect:
        table.uniqueKeys.values().every { it.columns*.name.every { String name -> columns.contains(name) } }
    }
}

@Entity
class DefectWritable {

    String inserted
    String updated
}

@Entity
class DefectReadOnlyColumns {

    String inserted
    String updated

    static mapping = {
        inserted insertable: false
        updated updatable: false
    }
}

@Entity
class DefectEnumGroup {

    DefectState state
    String other

    static mapping = {
        state unique: 'other'
    }
}

@Entity
class DefectCollectionGroup {

    String x
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags unique: 'x'
    }
}

enum DefectState {
    ON, OFF
}
