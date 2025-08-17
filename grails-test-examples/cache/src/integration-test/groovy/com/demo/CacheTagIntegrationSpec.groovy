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

package com.demo

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

import java.util.concurrent.TimeUnit

@Integration
class CacheTagIntegrationSpec extends ContainerGebSpec {

    void 'test clear cache'() {
        when:
        go('/demo/clearBlocksCache')

        then:
        pageSource.contains('cleared blocks cache')

        when:
        go('/demo/clearTemplatesCache')

        then:
        pageSource.contains('cleared templates cache')
    }

    void 'test block tag'() {
        when:
        go('/demo/blockCache?counter=5')

        then:
        $().text().contains('First block counter 6')
        $().text().contains('Second block counter 7')
        $().text().contains('Third block counter 8')

        when:
        go('/demo/blockCache?counter=42')

        then:
        $().text().contains('First block counter 6')
        $().text().contains('Second block counter 7')
        $().text().contains('Third block counter 8')
    }

    void 'test clear blocks cache'() {
        when:
        go('/demo/clearBlocksCache')

        then:
        pageSource.contains('cleared blocks cache')

        when:
        go('/demo/blockCache?counter=100')

        then:
        $().text().contains('First block counter 101')
        $().text().contains('Second block counter 102')
        $().text().contains('Third block counter 103')

        when:
        go('/demo/blockCache?counter=42')

        then:
        $().text().contains('First block counter 101')
        $().text().contains('Second block counter 102')
        $().text().contains('Third block counter 103')

        when:
        go('/demo/clearBlocksCache')

        then:
        pageSource.contains('cleared blocks cache')

        when:
        go('/demo/blockCache?counter=50')

        then:
        $().text().contains('First block counter 51')
        $().text().contains('Second block counter 52')
        $().text().contains('Third block counter 53')

        when:
        go('/demo/blockCache?counter=150')

        then:
        $().text().contains('First block counter 51')
        $().text().contains('Second block counter 52')
        $().text().contains('Third block counter 53')
    }

    void 'test expire blocks cache based on ttl'() {
        def ttlSeconds = 10

        when:
        go("/demo/blockCacheTTL?counter=100&ttl=${ttlSeconds}")

        then:
        $().text().contains('First block counter 101')
        $().text().contains('Second block counter 102')
        $().text().contains('Third block counter 103')

        when:
        go("/demo/blockCacheTTL?counter=42&ttl=${ttlSeconds}")

        then:
        $().text().contains('First block counter 101')
        $().text().contains('Second block counter 102')
        $().text().contains('Third block counter 103')
        TimeUnit.SECONDS.sleep(ttlSeconds)

        when:
        go("/demo/blockCacheTTL?counter=50&ttl=${ttlSeconds}")

        then:
        $().text().contains('First block counter 51')
        $().text().contains('Second block counter 52')
        $().text().contains('Third block counter 53')

        when:
        go("/demo/blockCacheTTL?counter=150&ttl=${ttlSeconds}")

        then:
        $().text().contains('First block counter 51')
        $().text().contains('Second block counter 52')
        $().text().contains('Third block counter 53')
    }

    void 'test render tag'() {
        when:
        go('/demo/renderTag?counter=1')

        then:
        $().text().contains('First invocation: Counter value: 1')
        $().text().contains('Second invocation: Counter value: 1')
        $().text().contains('Third invocation: Counter value: 3')
        $().text().contains('Fourth invocation: Counter value: 3')
        $().text().contains('Fifth invocation: Counter value: 1')

        when:
        go('/demo/renderTag?counter=5')

        then:
        $().text().contains('First invocation: Counter value: 1')
        $().text().contains('Second invocation: Counter value: 1')
        $().text().contains('Third invocation: Counter value: 3')
        $().text().contains('Fourth invocation: Counter value: 3')
        $().text().contains('Fifth invocation: Counter value: 1')

        when:
        go('/demo/clearTemplatesCache')

        then:
        pageSource.contains('cleared templates cache')

        when:
        go('/demo/renderTag?counter=5')

        then:
        $().text().contains('First invocation: Counter value: 5')
        $().text().contains('Second invocation: Counter value: 5')
        $().text().contains('Third invocation: Counter value: 7')
        $().text().contains('Fourth invocation: Counter value: 7')
        $().text().contains('Fifth invocation: Counter value: 5')

        when:
        go('/demo/renderTag?counter=1')

        then:
        $().text().contains('First invocation: Counter value: 5')
        $().text().contains('Second invocation: Counter value: 5')
        $().text().contains('Third invocation: Counter value: 7')
        $().text().contains('Fourth invocation: Counter value: 7')
        $().text().contains('Fifth invocation: Counter value: 5')
    }

    void 'test expire render cache based on ttl'() {
        def ttlSeconds = 10

        when:
        go("/demo/renderTagTTL?counter=1&ttl=${ttlSeconds}")

        then:
        $().text().contains('First invocation: Counter value: 1')
        $().text().contains('Second invocation: Counter value: 1')
        $().text().contains('Third invocation: Counter value: 3')
        $().text().contains('Fourth invocation: Counter value: 3')
        $().text().contains('Fifth invocation: Counter value: 1')

        when:
        go("/demo/renderTagTTL?counter=5&ttl=${ttlSeconds}")

        then:
        $().text().contains('First invocation: Counter value: 1')
        $().text().contains('Second invocation: Counter value: 1')
        $().text().contains('Third invocation: Counter value: 3')
        $().text().contains('Fourth invocation: Counter value: 3')
        $().text().contains('Fifth invocation: Counter value: 1')
        TimeUnit.SECONDS.sleep(ttlSeconds)

        when:
        go("/demo/renderTagTTL?counter=5&ttl=${ttlSeconds}")

        then:
        $().text().contains('First invocation: Counter value: 5')
        $().text().contains('Second invocation: Counter value: 5')
        $().text().contains('Third invocation: Counter value: 7')
        $().text().contains('Fourth invocation: Counter value: 7')
        $().text().contains('Fifth invocation: Counter value: 5')

        when:
        go("/demo/renderTagTTL?counter=1&ttl=${ttlSeconds}")

        then:
        $().text().contains('First invocation: Counter value: 5')
        $().text().contains('Second invocation: Counter value: 5')
        $().text().contains('Third invocation: Counter value: 7')
        $().text().contains('Fourth invocation: Counter value: 7')
        $().text().contains('Fifth invocation: Counter value: 5')
    }
}
