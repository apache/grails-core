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

package com.example

import grails.plugins.redis.RedisService
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Ignore
import redis.clients.jedis.Jedis

/**
 */
@Ignore // if not ignored, this spec expects that additional redis instances are running on localhost ports 6380 and 6381 and will fail without them
class RedisMultipleConnectionsConfigSpec extends Specification {

    @Autowired
    RedisService redisService
    @Autowired
    RedisService redisServiceOne
    @Autowired
    RedisService redisServiceTwo

    def setup() {
        redisService.flushDB()
        try {
            redisService.withConnection('one').flushDB()
        } catch(redis.clients.jedis.exceptions.JedisConnectionException e) {
            throw new Exception("You need to have redis running on port 6380 for this test exercising multiple redis connections to pass", e)
        }

        try {
            redisService.withConnection('two').flushDB()
        } catch(redis.clients.jedis.exceptions.JedisConnectionException e) {
            throw new Exception("You need to have redis running on port 6381 for this test exercising multiple redis connections to pass", e)
        }
        
        redisServiceOne.flushDB() //redundant, but just illustrates another way to do this
        redisServiceTwo.flushDB() //redundant, but just illustrates another way to do this
    }

    // if this test is failing, make sure you've got redis running on ports 6380 and 6381
    def "test multiple redis pools"() {
        given:
        def key = "key"
        def data = "data"

        when:
        redisService.withConnection('one').withRedis {Jedis redis ->
            redis.set(key, data)
        }

        then: 'These will both use the same redis connection'
        redisService.withConnection('one').withRedis {Jedis redis ->
            redis.get(key) == data
        }
        redisServiceOne.withRedis {Jedis redis ->
            redis.get(key) == data
        }

        and:
        redisService.withConnection('two').withRedis {Jedis redis ->
            !redis.get(key)
        }
        redisServiceTwo.withRedis {Jedis redis ->
            !redis.get(key)
        }
        redisService.withRedis {Jedis redis ->
            !redis.get(key)
        }
        redisService.withConnection('blahblahblah').withRedis {Jedis redis ->
            !redis.get(key)
        }

        when:
        redisService.withConnection('two').withRedis {Jedis redis ->
            redis.set(key, data)
        }

        then:
        redisService.withConnection('one').withRedis {Jedis redis ->
            redis.get(key) == data
        }
        redisServiceOne.withRedis {Jedis redis ->
            redis.get(key) == data
        }
        redisService.withConnection('two').withRedis {Jedis redis ->
            redis.get(key) == data
        }
        redisServiceTwo.withRedis {Jedis redis ->
            redis.get(key) == data
        }

        and:
        redisService.withRedis {Jedis redis ->
            !redis.get(key)
        }
        redisService.withConnection('blahblahblah').withRedis {Jedis redis ->
            !redis.get(key)
        }


        when:
        redisService.withRedis {Jedis redis ->
            redis.set(key, data)
        }

        then:
        redisService.withConnection('one').withRedis {Jedis redis ->
            redis.get(key) == data
        }
        redisService.withConnection('two').withRedis {Jedis redis ->
            redis.get(key) == data
        }
        redisService.withRedis {Jedis redis ->
            redis.get(key) == data
        }

        //will use default connection just lke normal redisService.withRedis since blahblahblah isn't valid
        redisService.withConnection('blahblahblah').withRedis {Jedis redis ->
            redis.get(key) == data
        }
    }
}
