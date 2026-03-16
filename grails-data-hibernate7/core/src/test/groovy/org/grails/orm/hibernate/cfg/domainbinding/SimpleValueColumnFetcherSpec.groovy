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
import org.hibernate.mapping.Column
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Table
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher

class SimpleValueColumnFetcherSpec extends HibernateGormDatastoreSpec {

    @Subject
    SimpleValueColumnFetcher fetcher = new SimpleValueColumnFetcher()

    def "should return first column when present"() {
        given:
        def table = new Table("test")
        def simpleValue = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column1 = new Column("col1")
        def column2 = new Column("col2")
        simpleValue.addColumn(column1)
        simpleValue.addColumn(column2)

        when:
        def result = fetcher.getColumnForSimpleValue(simpleValue)

        then:
        result == column1
    }

    def "should return null when columns are empty"() {
        given:
        def table = new Table("test")
        def simpleValue = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)

        when:
        def result = fetcher.getColumnForSimpleValue(simpleValue)

        then:
        result == null
    }
}
