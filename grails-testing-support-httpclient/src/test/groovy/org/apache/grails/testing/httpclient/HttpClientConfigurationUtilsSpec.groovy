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
package org.apache.grails.testing.httpclient

import java.time.Duration

import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class HttpClientConfigurationUtilsSpec extends Specification {

    @Unroll
    void 'fromSystemProperties parses duration format: #raw -> #expected'() {
        given:
        System.setProperty('micronaut.http.client.read-timeout', raw)

        when:
        def cfg = HttpClientConfigurationUtils.fromSystemProperties()

        then:
        cfg.readTimeout.orElse(null) == expected

        where:
        raw       || expected
        '20s'     || Duration.ofSeconds(20)
        '200ms'   || Duration.ofMillis(200)
        '1m'      || Duration.ofMinutes(1)
        '2h'      || Duration.ofHours(2)
        '1d'      || Duration.ofDays(1)
        '20000'   || Duration.ofMillis(20000)
        'PT20S'   || Duration.ofSeconds(20)
        'PT0.2S'  || Duration.ofMillis(200)
        ' 20s '   || Duration.ofSeconds(20)
    }

    void 'fromSystemProperties rejects invalid duration formats with helpful message'() {
        given:
        System.setProperty('micronaut.http.client.read-timeout', 'nope')

        when:
        HttpClientConfigurationUtils.fromSystemProperties()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('micronaut.http.client.read-timeout')
    }

    void 'fromSystemProperties rejects blank duration'() {
        given:
        System.setProperty('micronaut.http.client.read-timeout', '   ')

        when:
        HttpClientConfigurationUtils.fromSystemProperties()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('micronaut.http.client.read-timeout')
    }
}
