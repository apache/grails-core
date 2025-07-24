/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.TestEntity

/**
 * Created by graemerocher on 06/03/2017.
 */
class NotInListSpec extends GrailsDataTckSpec {

    void "test not in list returns the correct results"() {
        when:
        new TestEntity(name: "Fred").save()
        new TestEntity(name: "Bob").save()
        new TestEntity(name: "Jack").save(flush: true)

        then:
        TestEntity.countByNameNotInList(['Fred', "Bob"]) == 1
        TestEntity.findByNameNotInList(['Fred', "Bob"]).name == "Jack"
    }
}
