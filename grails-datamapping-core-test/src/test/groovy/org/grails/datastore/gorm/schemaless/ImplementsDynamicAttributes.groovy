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
package org.grails.datastore.gorm.schemaless

import spock.lang.Issue
import spock.lang.Specification


class DynamicDomainSpec extends Specification {

    @Issue("GDM-769")
    void "Test a domain with dynamic attributes doesn't try to set readonly properties"() {
        given:
        DynamicEntity entity = new DynamicEntity()

        when:
        entity.putAt("foo", 123)
        entity.putAt("name", "Sally")

        then:
        entity.foo == "foo"
        entity.name == "Sally"
        entity.getAt("foo") == "foo"
        !entity.attributes().containsKey("name")
        entity.attributes().foo == 123
    }

}

class DynamicEntity implements DynamicAttributes {

    String name

    String getFoo() {
        "foo"
    }

}