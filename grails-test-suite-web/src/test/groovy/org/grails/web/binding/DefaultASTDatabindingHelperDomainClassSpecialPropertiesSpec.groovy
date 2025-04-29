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
package org.grails.web.binding

import grails.persistence.Entity
import spock.lang.Issue
import spock.lang.Specification

class DefaultASTDatabindingHelperDomainClassSpecialPropertiesSpec extends
        Specification {

    @Issue('GRAILS-11173')
    void 'Test binding to special properties in a domain class'() {
        when:
        Date now = new Date()
        SomeDomainClass obj = new SomeDomainClass(dateCreated: now, lastUpdated: now)
        
        then:
        obj.dateCreated == null
        obj.lastUpdated == null
    }
    
    @Issue('GRAILS-11173')
    void 'Test binding to special properties in a domain class with explicit bindable rules'() {
        when:
        def now = new Date()
        def obj = new SomeDomainClassWithExplicitBindableRules(dateCreated: now, lastUpdated: now)
        
        then:
        obj.dateCreated == now
        obj.lastUpdated == now
    }
}
        
@Entity
class SomeDomainClass {
    Date dateCreated
    Date lastUpdated
}

@Entity
class SomeDomainClassWithExplicitBindableRules {
    Date dateCreated
    Date lastUpdated

    static constraints = {
        dateCreated bindable: true
        lastUpdated bindable: true
    }
}
