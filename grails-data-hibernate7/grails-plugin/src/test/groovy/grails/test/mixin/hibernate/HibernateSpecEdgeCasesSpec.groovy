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
package grails.test.mixin.hibernate

import grails.test.hibernate.HibernateSpec
import org.springframework.context.support.GenericApplicationContext

class HibernateSpecEdgeCasesSpec extends HibernateSpec {

    def "test HibernateSpec with empty domain classes"() {
        expect:
        hibernateDatastore != null
    }
    
    List<Class> getDomainClasses() { [] }
}

class HibernateSpecAppContextExistsSpec extends HibernateSpec {

    void setupSpec() {
        applicationContext = new GenericApplicationContext()
        applicationContext.refresh()
    }

    def "test HibernateSpec with existing applicationContext"() {
        expect:
        hibernateDatastore != null
        applicationContext != null
    }

    List<Class> getDomainClasses() { [] }
}
