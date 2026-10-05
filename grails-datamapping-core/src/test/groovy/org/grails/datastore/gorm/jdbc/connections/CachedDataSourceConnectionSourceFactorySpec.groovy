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
package org.grails.datastore.gorm.jdbc.connections

import spock.lang.Specification

class CachedDataSourceConnectionSourceFactorySpec extends Specification {

    void "create(name, settings) returns the same cached instance for the same name"() {
        given:
        def factory = new CachedDataSourceConnectionSourceFactory()
        def settings = new DataSourceSettings(url: 'jdbc:h2:mem:cachedFactoryTest1;DB_CLOSE_DELAY=-1')

        when:
        def first = factory.create('default', settings)
        def second = factory.create('default', settings)

        then:
        first.is(second)

        cleanup:
        first?.close()
    }

    void "create(name, settings) returns a different instance for a different name"() {
        given:
        def factory = new CachedDataSourceConnectionSourceFactory()
        def settingsA = new DataSourceSettings(url: 'jdbc:h2:mem:cachedFactoryTest2a;DB_CLOSE_DELAY=-1')
        def settingsB = new DataSourceSettings(url: 'jdbc:h2:mem:cachedFactoryTest2b;DB_CLOSE_DELAY=-1')

        when:
        def a = factory.create('sourceA', settingsA)
        def b = factory.create('sourceB', settingsB)

        then:
        !a.is(b)

        cleanup:
        a?.close()
        b?.close()
    }
}
