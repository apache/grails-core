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

package grails.plugins.redis

import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Ignore
import spock.lang.Specification

@Integration
@Rollback
@SpringBootTest
class RedisTagLibSpec extends Specification {

    @Autowired GrailsApplication grailsApplication
    @Autowired RedisService redisService
    RedisTagLib tagLib

    protected static KEY = "RedisTagLibTests:memoize"
    protected static CONTENTS = "expected contents"
    protected static FAIL_BODY = "unexpected contents, should not have this"

    def setup() {
        redisService.flushDB()
         tagLib= grailsApplication.mainContext.getBean(RedisTagLib)
    }

    @Ignore
    def testMemoize() {
        when:
        String result = tagLib.memoize([key: KEY], { -> CONTENTS })

        then:
        CONTENTS == result

        when:
        result = tagLib.memoize([key: KEY], { -> FAIL_BODY })
        then:
        CONTENTS == result // won't find $FAIL_BODY
    }

    @Ignore
    def testMemoizeTTL() {
        when:
        String result = tagLib.memoize([key: 'no-ttl-test'], { -> CONTENTS }).toString()

        then:
        CONTENTS == result
        redisService.NO_EXPIRATION_TTL == redisService.ttl("no-ttl-test")

        when:
        result = tagLib.memoize([key: 'ttl-test', expire: 60], { -> CONTENTS }).toString()

        then:
        CONTENTS == result
        redisService.ttl("ttl-test") > 0
    }
}
