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

import org.apache.grails.data.testing.tck.domains.ChildEntity
import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Abstract base test for loading proxies. Subclasses should do the necessary setup to configure GORM
 */
class ProxyLoadingSpec extends GrailsDataTckSpec {

    void "Test load proxied instance directly"() {

        given:
        def t = new TestEntity(name: "Bob", age: 45, child: new ChildEntity(name: "Test Child")).save(flush: true)

        when:
        def proxy = TestEntity.load(t.id)

        then:
        proxy != null
        t.id == proxy.id
        "Bob" == proxy.name
    }

    void "Test query using proxied association"() {
        given:
        def child = new ChildEntity(name: "Test Child")
        def t = new TestEntity(name: "Bob", age: 45, child: child).save()

        when:
        def proxy = ChildEntity.load(child.id)
        t = TestEntity.findByChild(proxy)

        then:
        t != null
    }
}
