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

package org.grails.forge.feature.cache

import org.grails.forge.BeanContextSpec
import spock.lang.Ignore
import spock.lang.Unroll

class CacheSpec extends BeanContextSpec {

    @Ignore("There is only one cache implementation right now")
    void 'test there can only be one cache feature'() {
        when:
        getFeatures(["cache-ehcache"])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("There can only be one of the following features selected")
    }

    @Unroll
    void 'cache can be selected with #otherCache feature'() {
        when:
        getFeatures(["cache", otherCache])

        then:
        noExceptionThrown()

        where:
        otherCache << ["cache-ehcache"]
    }

}
