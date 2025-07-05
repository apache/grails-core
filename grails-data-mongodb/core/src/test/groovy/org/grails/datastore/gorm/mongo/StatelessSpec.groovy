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

import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class StatelessSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Volcano])
    }

    void "stateless and self-assigned ids can be used together"() {
        given:
        Volcano v = new Volcano(country: "Spain")
        v.id = "Teide"
        v.insert flush: true
        manager.session.clear()

        when:
        v = Volcano.get("Teide")

        then:
        v.id == "Teide"
        v.country == "Spain"

        when:
        manager.session.clear()
        v = Volcano.get("Teide")
        v.country = 'España'
        v.save flush: true
        manager.session.clear()
        v = Volcano.get("Teide")

        then:
        v.id == "Teide"
        v.country == "España"
    }


}


