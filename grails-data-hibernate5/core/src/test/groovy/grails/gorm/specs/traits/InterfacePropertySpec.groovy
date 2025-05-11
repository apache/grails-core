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
package grails.gorm.specs.traits

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 29/05/2017.
 */
class InterfacePropertySpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([TestDomain])
    }

    @Issue('https://github.com/grails/gorm-hibernate5/issues/38')
    void "test interface that exposes id"() {
        when:
        TestDomain td = new TestDomain(name: "Fred").save(flush: true)

        then:
        td.id
        TestDomain.first().id
    }
}

interface ObjectId<T> extends Serializable {
    T getId()

    void setId(T id)
}

@Entity
class TestDomain implements ObjectId<Long> {

    Long id
    String name

    static constraints = {
    }
}
