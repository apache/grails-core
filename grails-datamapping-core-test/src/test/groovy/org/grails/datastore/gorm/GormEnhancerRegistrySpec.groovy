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
package org.grails.datastore.gorm

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.Specification

/**
 * Tests for {@link GormEnhancerRegistry}
 */
class GormEnhancerRegistrySpec extends Specification {

    void 'test singleton instance'() {
        given:
        def registry1 = GormEnhancerRegistry.getInstance()
        def registry2 = GormEnhancerRegistry.getInstance()

        expect:
        registry1.is(registry2)
    }

    void 'test preferred datastore thread-local isolation'() {
        given:
        def registry = GormEnhancerRegistry.getInstance()
        def ds1 = Mock(Datastore)
        def ds2 = Mock(Datastore)

        when:
        registry.setPreferredDatastore(ds1)

        then:
        registry.getPreferredDatastore() == ds1

        when:
        registry.setPreferredDatastore(ds2)

        then:
        registry.getPreferredDatastore() == ds2

        when:
        registry.clearPreferredDatastore()

        then:
        registry.getPreferredDatastore() == null
    }

    void 'test resolving datastore depth tracking'() {
        given:
        def registry = GormEnhancerRegistry.getInstance()

        expect:
        registry.getResolvingDatastoreDepth() == 0

        when:
        registry.setResolvingDatastoreDepth(1)

        then:
        registry.getResolvingDatastoreDepth() == 1

        when:
        registry.setResolvingDatastoreDepth(2)

        then:
        registry.getResolvingDatastoreDepth() == 2

        when:
        registry.clearResolvingDatastoreDepth()

        then:
        registry.getResolvingDatastoreDepth() == 0
    }

    void 'test thread isolation for preferred datastore'() {
        given:
        def registry = GormEnhancerRegistry.getInstance()
        def ds1 = Mock(Datastore)
        def ds2 = Mock(Datastore)
        def mainThreadDs = null
        def threadDs = null
        def thread = null

        when:
        registry.setPreferredDatastore(ds1)
        mainThreadDs = registry.getPreferredDatastore()

        then:
        mainThreadDs == ds1

        when:
        thread = Thread.start {
            registry.setPreferredDatastore(ds2)
            threadDs = registry.getPreferredDatastore()
        }
        thread.join()

        then:
        threadDs == ds2
        registry.getPreferredDatastore() == ds1
    }

    void cleanup() {
        def registry = GormEnhancerRegistry.getInstance()
        registry.clearPreferredDatastore()
        registry.clearResolvingDatastoreDepth()
    }
}

