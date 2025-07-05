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
import spock.lang.Issue

/**
 * Created by graemerocher on 20/04/16.
 */
class CountByWithEmbeddedSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([CountByPerson])
    }

    @Issue('https://github.com/grails/grails-core/issues/9846')
    void "Test countBy query with embedded entity"() {
        given:
        new CountByPerson(name: "Fred", bornInCountry: new CountByCountry(name: "England")).save(flush: true)
        new CountByPerson(bornInCountry: new CountByCountry(name: "Scotland")).save(flush: true)

        expect:
        CountByPerson.countByNameIsNotNull() == 1
    }
}

@Entity
class CountByPerson {
    String name
    CountByCountry bornInCountry

    static embedded = ['bornInCountry']

    static constraints = {
        name nullable: true
        bornInCountry nullable: true
    }
}

class CountByCountry {
    String name
}
