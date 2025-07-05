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

import grails.gorm.specs.entities.Club
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DeleteAllWhereSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Club])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/969')
    void "test delete all type conversion"() {
        given:
        new Club(name: "Manchester United").save()
        new Club(name: "Arsenal").save(flush: true)

        when:
        int count = Club.count

        then:
        count == 2

        when:
        def idList = [Club.findByName("Arsenal").id as Integer]
        Club.where {
            id in idList
        }.deleteAll()

        then:
        Club.count == 1
        Club.findByName("Manchester United")
    }
}
