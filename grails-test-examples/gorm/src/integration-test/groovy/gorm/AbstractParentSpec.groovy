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
import spock.lang.Specification

@Integration(applicationClass = Application)
@Rollback
class AbstractParentSpec extends Specification {

    void "Test that persisting a domain class with an abstract parent works"() {
        when:"An entity with an abstract parent is persisted"
            new ChildA(name:"A", ageA:10).save(flush:true)
            new ChildB(name:"B", ageB:15).save(flush:true)

        then:"The result is correct"
            AbstractParent.count() == 2
    }
}
