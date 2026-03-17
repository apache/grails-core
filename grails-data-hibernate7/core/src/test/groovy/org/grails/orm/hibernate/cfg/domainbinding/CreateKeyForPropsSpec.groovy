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

package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.hibernate.MappingException
import org.hibernate.mapping.Table
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueKeyForColumnsCreator

class CreateKeyForPropsSpec extends Specification {

    def "creates unique key when property is unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) {
            getHibernateOwner() >> owner
        }

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1", "p2"]
        }
        grailsProp.getMappedForm() >> mappedForm

        def otherProp1 = Mock(HibernatePersistentProperty)
        def otherProp2 = Mock(HibernatePersistentProperty)
        owner.getHibernatePropertyByName("p1") >> otherProp1
        owner.getHibernatePropertyByName("p2") >> otherProp2

        String path = "some_path"
        def table = new Table("t")
        String baseColumnName = "base_col"

        columnNameFetcher.getColumnNameForPropertyAndPath(otherProp1, path, null) >> "col1"
        columnNameFetcher.getColumnNameForPropertyAndPath(otherProp2, path, null) >> "col2"

        when:
        subject.createKeyForProps(grailsProp, path, table, baseColumnName)

        then:
        1 * grailsProp.getMappedForm() >> mappedForm
        1 * grailsProp.getHibernateOwner() >> owner
        1 * mappedForm.isUnique() >> true
        1 * mappedForm.isUniqueWithinGroup() >> true
        1 * mappedForm.getUniquenessGroup() >> ["p1", "p2"]
        1 * owner.getHibernatePropertyByName("p1") >> otherProp1
        1 * owner.getHibernatePropertyByName("p2") >> otherProp2
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(otherProp1, path, null)
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(otherProp2, path, null)
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, _ as List)
    }

    def "does nothing when property is not unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) { getHibernateOwner() >> owner }

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> false
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1"]
        }
        grailsProp.getMappedForm() >> mappedForm

        when:
        subject.createKeyForProps(grailsProp, null, new Table("t"), "base")

        then:
        1 * grailsProp.getMappedForm() >> mappedForm
        0 * grailsProp.getHibernateOwner() >> owner
        1 * mappedForm.isUnique() >> false
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }

    def "throws when uniqueness group references unknown property"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) { getHibernateOwner() >> owner }
        owner.getJavaClass() >> CreateKeyForPropsSpec

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["missingProp"]
        }
        grailsProp.getMappedForm() >> mappedForm

        owner.getHibernatePropertyByName("missingProp") >> null

        when:
        subject.createKeyForProps(grailsProp, null, new Table("t"), "base")

        then:
        thrown(MappingException)
        1 * grailsProp.getMappedForm() >> mappedForm
        1 * grailsProp.getHibernateOwner() >> owner
        1 * mappedForm.isUnique() >> true
        1 * mappedForm.isUniqueWithinGroup() >> true
        1 * mappedForm.getUniquenessGroup() >> ["missingProp"]
        1 * owner.getJavaClass() >> CreateKeyForPropsSpec
        1 * owner.getHibernatePropertyByName("missingProp")
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }
}
