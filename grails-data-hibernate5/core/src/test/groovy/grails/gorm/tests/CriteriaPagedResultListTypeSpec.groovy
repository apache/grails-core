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

package grails.gorm.tests

import grails.gorm.annotation.Entity
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14339')
class CriteriaPagedResultListTypeSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(PagedTypeItem)
    }

    void setup() {
        (1..5).each { new PagedTypeItem(name: "n$it").save() }
        session.flush()
        session.clear()
    }

    void "a paged criteria list is both the common and the Hibernate paged result list type"() {
        when:
        def result = PagedTypeItem.createCriteria().list(max: 2) { order 'name' }

        then:
        result instanceof grails.gorm.PagedResultList
        result instanceof org.grails.orm.hibernate.query.PagedResultList
        result*.name == ['n1', 'n2']
        ((org.grails.orm.hibernate.query.PagedResultList) result).totalCount == 5
    }

    void "a page beyond the last has no elements but keeps the total count"() {
        when:
        def result = PagedTypeItem.createCriteria().list(max: 2, offset: 50) { order 'name' }

        then:
        result.isEmpty()
        ((grails.gorm.PagedResultList) result).totalCount == 5
        ((org.grails.orm.hibernate.query.PagedResultList) result).totalCount == 5
    }
}

@Entity
class PagedTypeItem {
    String name
}
