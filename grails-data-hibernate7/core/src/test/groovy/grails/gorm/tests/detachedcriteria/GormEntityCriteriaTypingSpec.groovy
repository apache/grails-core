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
package grails.gorm.tests.detachedcriteria

import groovy.transform.CompileStatic

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec

class GormEntityCriteriaTypingSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(TypedCriteriaEntity)
    }

    void "the criteria closures of the static GORM API type check under CompileStatic and run"() {
        given:
        (1..4).each { new TypedCriteriaEntity(name: "n${it}", amount: it).save(flush: true) }

        expect:
        TypedCriteriaCaller.whereCount(2) == 2
        TypedCriteriaCaller.whereAnyNames(1, 4) == ['n1', 'n4']
        TypedCriteriaCaller.lazyCount(3) == 1
        TypedCriteriaCaller.findAllNames(3) == ['n4']
        TypedCriteriaCaller.findAllWithArgsNames(1) == ['n4', 'n3']
    }
}

@CompileStatic
class TypedCriteriaCaller {

    static int whereCount(int above) {
        TypedCriteriaEntity.where { gt('amount', above) }.count() as int
    }

    static List<String> whereAnyNames(int first, int second) {
        TypedCriteriaEntity.whereAny { eq('amount', first); eq('amount', second) }.list(sort: 'amount')*.name
    }

    static int lazyCount(int above) {
        TypedCriteriaEntity.whereLazy { gt('amount', above) }.count() as int
    }

    static List<String> findAllNames(int above) {
        TypedCriteriaEntity.findAll { gt('amount', above) }*.name
    }

    static List<String> findAllWithArgsNames(int above) {
        TypedCriteriaEntity.findAll([sort: 'amount', order: 'desc', max: 2]) { gt('amount', above) }*.name
    }
}

@Entity
class TypedCriteriaEntity implements HibernateEntity<TypedCriteriaEntity> {
    String name
    Integer amount
}
