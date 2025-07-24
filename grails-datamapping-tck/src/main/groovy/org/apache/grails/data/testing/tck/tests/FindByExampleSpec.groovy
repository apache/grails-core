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
import org.apache.grails.data.testing.tck.domains.Plant

class FindByExampleSpec extends GrailsDataTckSpec {

    def "Test findAll by example"() {
        given:
        new Plant(name: "Pineapple", goesInPatch: false).save()
        new Plant(name: "Cabbage", goesInPatch: true).save()
        new Plant(name: "Kiwi", goesInPatch: false).save(flush: true)
        manager.session.clear()
        when:
        def results = Plant.findAll(new Plant(goesInPatch: false))
        then:
        results.size() == 2
        "Pineapple" in results*.name
        "Kiwi" in results*.name

        when:
        results = Plant.findAll(new Plant(name: "Cabbage", goesInPatch: false))

        then:
        results.size() == 0

        when:
        results = Plant.findAll(new Plant(name: "Cabbage", goesInPatch: true))

        then:
        results.size() == 1
        "Cabbage" in results*.name
    }

    def "Test find by example"() {
        given:
        new Plant(name: "Pineapple", goesInPatch: false).save()
        new Plant(name: "Cabbage", goesInPatch: true).save()
        new Plant(name: "Kiwi", goesInPatch: false).save(flush: true)
        manager.session.clear()

        when:
        Plant result = Plant.find(new Plant(name: "Cabbage", goesInPatch: false))

        then:
        result == null

        when:
        result = Plant.find(new Plant(name: "Cabbage", goesInPatch: true))

        then:
        result != null
        result.name == "Cabbage"
    }
}
