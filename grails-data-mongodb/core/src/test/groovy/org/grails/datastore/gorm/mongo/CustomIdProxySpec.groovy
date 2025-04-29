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
package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.proxy.EntityProxy
import spock.lang.Issue

/**
 * Created by graemerocher on 14/10/16.
 */
class CustomIdProxySpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([CustomIdCompany, CustomIdTeam])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/813')
    void "Test custom id with proxies"() {
        when:
        CustomIdCompany c = new CustomIdCompany([slug: 'mycompany']).insert()
        CustomIdTeam t = new CustomIdTeam([slug: 'myteam', company: c]).insert(flush: true)
        manager.session.clear()
        t = CustomIdTeam.findBySlug('myteam')

        then:
        t.company instanceof EntityProxy
        !t.company.isInitialized()
        t.company.slug == 'mycompany'
    }
}

@Entity
class CustomIdCompany {
    String slug
    static mapping = {
        id name: 'slug'
    }
}

@Entity
class CustomIdTeam {
    String slug
    CustomIdCompany company
    static mapping = {
        id name: 'slug'
    }
}