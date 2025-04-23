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

package gorm

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * Created by graemerocher on 31/01/2017.
 */
import grails.util.GrailsWebMockUtil
import spock.lang.*

@Integration(applicationClass = Application)
@Rollback
class BindingOutsideRequestSpec extends Specification {

    void "Save nested company without webrequest (Quartz/Bpotstrap)"() {
        given:
        def company = new Company(relation: new CompanyRelation(address: new RelationAddress()))
        when:
        company.validate()
        then:
        !company.errors.hasErrors()
        when:
        company.save()
        then:
        !company.errors.hasErrors()
    }
    void "Save nested company nested with webrequest"() {
        given:
        GrailsWebMockUtil.bindMockWebRequest()
        def company = new Company(relation: new CompanyRelation(address: new RelationAddress()))
        when:
        company.validate()
        then:
        !company.hasErrors()
        when:
        company.save()
        then:
        !company.hasErrors()
    }
}
