/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.plugins.redis

import org.springframework.beans.factory.annotation.Autowired

class RedisTagLib {

    @Autowired
    RedisService redisService

    static namespace = 'redis'

    def memoize = { attrs, body ->
        String key = attrs.key
        if (!key) throw new IllegalArgumentException('[key] attribute must be specified for memoize!')

        Integer expire = attrs.expire?.toInteger()

        out << redisService.memoize(key, expire) { body() ?: '' }
    }
}
