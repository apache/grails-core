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

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.MappingException
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table

import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder

class SimpleValueColumnBinderSpec extends HibernateGormDatastoreSpec {

    void "Test defaults"() {
        when:
        def type = "String"
        def columnName = "columnName"
        def tableName = "table"
        def contributor = "contributor"
        def nullable = false
        def simpleValueBinder = new SimpleValueColumnBinder()
        Table table = new Table(contributor,tableName);
        table.setName(tableName)
        def grailsDomainBinder = getGrailsDomainBinder()
        BasicValue simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, table);
        simpleValueBinder.bindSimpleValue(simpleValue, type, columnName, nullable)

        def column = (Column) simpleValue.column
        then:
        column
        column.value == simpleValue
        column.name == columnName
        !column.nullable
        simpleValue.column == column
        table.getColumn(0) == column
    }

    void "Test no table"() {
        when:
        def type = "String"
        def columnName = "columnName"
        def nullable = true
        def simpleValueBinder = new SimpleValueColumnBinder()
        def grailsDomainBinder = getGrailsDomainBinder()
        BasicValue simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, null);
        simpleValueBinder.bindSimpleValue(simpleValue, type, columnName, nullable)

        then:
        MappingException e = thrown()

    }
}
