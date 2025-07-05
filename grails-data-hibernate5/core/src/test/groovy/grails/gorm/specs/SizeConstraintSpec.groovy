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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Issue

/**
 * Created by graemerocher on 25/01/2017.
 */
class SizeConstraintSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([SizeConstrainedUser])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/846')
    void "test size constraint is used in schema"() {
        when:"A constraint is violated"
        new SizeConstrainedUser(username:"blah", columnAa:"123456", columnBb:"123456").save(flush:true, validate:false)

        then:"an exception is thrown"
        thrown(DataIntegrityViolationException)

        when:"A constraint is violated"
        new SizeConstrainedUser(username:"blah", columnAa:"123456", columnBb:"12345").save(flush:true, validate:false)

        then:"an exception is thrown"
        thrown(DataIntegrityViolationException)

        when:"A constraints are not violated"
        manager.session.clear()
        new SizeConstrainedUser(username:"blah", columnAa:"12345", columnBb:"12345").save(flush:true, validate:false)

        then:"the insert occurred"
        SizeConstrainedUser.count() == 1

    }
}

@Entity
class SizeConstrainedUser {
    String username
    String columnAa
    String columnBb

    static constraints = {
        username(blank: false)
        columnAa(nullable: true, size: 0..5)
        columnBb(nullable: true, maxSize: 5)
    }
}
