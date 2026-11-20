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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class RLikeSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {
    void setupSpec() {
        manager.registerDomainClasses(RLikeLegacyFoo)
    }

    void "test rlike works with H2"() {
        given:
        new RLikeLegacyFoo(name: "ABC").save(flush: true)
        new RLikeLegacyFoo(name: "ABCDEF").save(flush: true)
        new RLikeLegacyFoo(name: "ABCDEFGHI").save(flush: true)

        when:
        manager.session.clear()
        List<RLikeLegacyFoo> allFoos = RLikeLegacyFoo.findAllByNameRlike("ABCD.*")

        then:
        allFoos.size() == 2
    }
}

@Entity
class RLikeLegacyFoo {
    String name
}
