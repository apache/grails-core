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
import grails.spring.BeanBuilder
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction
import redis.clients.jedis.exceptions.JedisConnectionException
import spock.lang.Specification

import java.util.concurrent.*

import static grails.plugins.redis.RedisService.NO_EXPIRATION_TTL

@Integration
@SpringBootTest
class RedisServiceSpec extends Specification {
    @Autowired RedisService redisService
    @Autowired GrailsApplication grailsApplication
    RedisService redisServiceMock

    def "application context wired up"() {
        when:
        def app = grailsApplication

        then:
        app
    }

    void setup() {
        redisServiceMock = mockRedisServiceForFailureTest(getNewInstanceOfBean(RedisService))
        try {
            redisService.flushDB()
        }
        catch (JedisConnectionException jce) {
            // swallow connect exception so failure tests can proceed
        }
    }

    def "attempt pool exhaustion and redis.close()"() {
        given:
        Integer loopCount = 50
        ExecutorService taskExecutor = Executors.newWorkStealingPool(loopCount)
        CountDownLatch latch = new CountDownLatch(loopCount)
        ConcurrentMap<Integer, Boolean> exceptionStatusMap = new ConcurrentHashMap<>()

        when:
        List<Callable> tasks = []
        loopCount.times { Integer loop ->
            tasks.add(new Callable(){
                @Override
                String call() throws Exception {
                    Boolean hasException = false
                    try {
                        redisService.withRedis { Jedis redis ->
                            println "Starting ${loop}"
                            Thread.sleep(2000)
                            latch.countDown()
                            println "Completed ${loop}"
                        }
                    } catch (Exception e) {
                        hasException = true
                    } finally {
                        exceptionStatusMap.putIfAbsent(loop, hasException)
                    }
                }
            })
        }
        taskExecutor.invokeAll(tasks)

        try {
            latch.await()
            taskExecutor.shutdown()
        } catch (Exception e) {
            assert false
        }

        then:
        !exceptionStatusMap.containsValue(true)
    }

    def "attempt pool exhaustion and redis.close() through exeptions"() {
        given:
        Integer loopCount = 50
        ExecutorService taskExecutor = Executors.newWorkStealingPool(loopCount)
        CountDownLatch latch = new CountDownLatch(loopCount)
        ConcurrentMap<Integer, Boolean> exceptionStatusMap = new ConcurrentHashMap<>()

        when:
        List<Callable> tasks = []
        loopCount.times { Integer loop ->
            tasks.add(new Callable(){
                @Override
                String call() throws Exception {
                    Boolean hasException = false
                    try {
                        redisService.withRedis { Jedis redis ->
                            latch.countDown()
                            throw new RuntimeException("BOOM!")
                        }
                    } catch (Exception e) {
                        hasException = true
                    } finally {
                        exceptionStatusMap.putIfAbsent(loop, hasException)
                    }
                }
            })
        }
        taskExecutor.invokeAll(tasks)

        try {
            latch.await()
            taskExecutor.shutdown()
        } catch (Exception e) {
            assert false
        }

        then:
        exceptionStatusMap.containsValue(true)
    }

    def testFlushDB() {
        given:
        // actually called as part of setup too, but we can test it here
        redisService.withRedis { Jedis redis ->
            assert 0 == redis.dbSize()
            redis.set("foo", "bar")
            assert 1 == redis.dbSize()
        }

        when:
        def size = -1
        redisService.flushDB()
        redisService.withRedis { Jedis redis ->
            size = redis.dbSize()
        }

        then:
        size == 0
    }

    def testMemoizeKeyWithNoRedis() {
        given:
        def calledCount = 0

        def cacheMissClosure = {
            calledCount += 1
            return "foo"
        }

        when:
        def cacheMissResult = redisServiceMock.memoize("mykey", cacheMissClosure)

        then:
        1 == calledCount
        "foo" == cacheMissResult

        when:
        cacheMissResult = redisServiceMock.memoize("mykey", cacheMissClosure)

        then:
        2 == calledCount
        "foo" == cacheMissResult
    }


    void testMemoizeKey() {
        given:
        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return "foo"
        }

        when:
        def cacheMissResult = redisService.memoize("mykey", cacheMissClosure)

        then:
        1 == calledCount
        "foo" == cacheMissResult
        NO_EXPIRATION_TTL == redisService.ttl("mykey")

        when:
        def cacheHitResult = redisService.memoize("mykey", cacheMissClosure)

        then: "should have hit the cache, not called our method again"
        1 == calledCount
        "foo" == cacheHitResult
    }


    def testMemoizeKeyWithExpire() {
        given:
        assert 0 > redisService.ttl("mykey")

        when:
        def result = redisService.memoize("mykey", 60) { "foo" }

        then:
        "foo" == result
        NO_EXPIRATION_TTL < redisService.ttl("mykey")
    }

    void testMemoizeKeyNullValue() {
        given:
        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return null
        }

        when:
        def cacheMissResult = redisServiceMock.memoize("mykey", cacheMissClosure)

        then:
        assert 1 == calledCount
        assert null == cacheMissResult

        when:
        def cacheMissAgainResult = redisServiceMock.memoize("mykey", cacheMissClosure)

        then: "should have called the method again if we got a null"
        assert 2 == calledCount
        assert null == cacheMissAgainResult
    }


    void testMemoizeHashFieldWithoutRedis() {
        given:
        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return "foo"
        }

        when:
        def cacheMissResult = redisServiceMock.memoizeHashField("mykey", "first", cacheMissClosure)

        then:
        1 == calledCount
        "foo" == cacheMissResult

        when:
        cacheMissResult = redisServiceMock.memoizeHashField("mykey", "first", cacheMissClosure)

        then: "should have hit the cache, not called our method again"
        2 == calledCount
        "foo" == cacheMissResult
    }


    void testMemoizeHashField() {
        given:
        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return "foo"
        }

        when:
        def cacheMissResult = redisService.memoizeHashField("mykey", "first", cacheMissClosure)

        then:
        1 == calledCount
        "foo" == cacheMissResult
        NO_EXPIRATION_TTL == redisService.ttl("mykey")

        when:
        def cacheHitResult = redisService.memoizeHashField("mykey", "first", cacheMissClosure)

        then: "should have hit the cache, not called our method again"
        1 == calledCount
        "foo" == cacheHitResult

        when:
        def cacheMissSecondResult = redisService.memoizeHashField("mykey", "second", cacheMissClosure)

        then: "cache miss because we're using a different field in the same key"
        2 == calledCount
        "foo" == cacheMissSecondResult
    }


    void testMemoizeHashFieldWithExpire() {
        given:
        assert 0 > redisService.ttl("mykey")

        when:
        def result = redisService.memoizeHashField("mykey", "first", 60) { "foo" }

        then:
        "foo" == result
        NO_EXPIRATION_TTL < redisService.ttl("mykey")
    }

    void testMemoizeHashWithoutRedis() {
        given:
        def calledCount = 0
        def expectedHash = [foo: 'bar', baz: 'qux']
        def cacheMissClosure = {
            calledCount += 1
            return expectedHash
        }

        when:
        def cacheMissResult = redisServiceMock.memoizeHash("mykey", cacheMissClosure)

        then:
        1 == calledCount
        expectedHash == cacheMissResult

        when:
        def cacheHitResult = redisServiceMock.memoizeHash("mykey", cacheMissClosure)

        then: "should have hit the cache, not called our method again"
        2 == calledCount
        expectedHash == cacheHitResult
    }


    void testMemoizeHash() {
        given:
        def calledCount = 0
        def expectedHash = [foo: 'bar', baz: 'qux']
        def cacheMissClosure = {
            calledCount += 1
            return expectedHash
        }

        when:
        def cacheMissResult = redisService.memoizeHash("mykey", cacheMissClosure)

        then:
        assert 1 == calledCount
        assert expectedHash == cacheMissResult
        assert NO_EXPIRATION_TTL == redisService.ttl("mykey")

        when:
        def cacheHitResult = redisService.memoizeHash("mykey", cacheMissClosure)

        then: "should have hit the cache, not called our method again"
        assert 1 == calledCount
        assert expectedHash == cacheHitResult
    }


    void testMemoizeHashWithExpire() {
        given:
        def expectedHash = [foo: 'bar', baz: 'qux']
        assert 0 > redisService.ttl("mykey")

        when:
        def result = redisService.memoizeHash("mykey", 60) { expectedHash }

        then:
        assert expectedHash == result
        assert NO_EXPIRATION_TTL < redisService.ttl("mykey")
    }

    void testMemoizeListWithoutRedis() {
        given:
        def book1 = "book1"
        def book2 = "book2"
        def book3 = "book3"
        List books = [book1, book2, book3]

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return books
        }

        when:
        def cacheMissList = redisServiceMock.memoizeList("mykey", cacheMissClosure)

        then:
        assert 1 == calledCount
        assert [book1, book2, book3] == cacheMissList

        when:
        List cacheHitList = redisServiceMock.memoizeList("mykey", cacheMissClosure)

        then: "cache hit, don't call closure again"
        assert 2 == calledCount
        assert [book1, book2, book3] == cacheHitList
        assert cacheMissList == cacheHitList
    }


    void testMemoizeList() {
        given:
        def book1 = "book1"
        def book2 = "book2"
        def book3 = "book3"
        List books = [book1, book2, book3]

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return books
        }

        when:
        def cacheMissList = redisService.memoizeList("mykey", cacheMissClosure)

        then:
        1 == calledCount
        [book1, book2, book3] == cacheMissList
        NO_EXPIRATION_TTL == redisService.ttl("mykey")

        when:
        List cacheHitList = redisService.memoizeList("mykey", cacheMissClosure)

        then: "cache hit, don't call closure again"
        assert 1 == calledCount
        assert [book1, book2, book3] == cacheHitList
        assert cacheMissList == cacheHitList
    }


    void testMemoizeListWithExpire() {
        given:
        def book1 = "book1"
        assert 0 > redisService.ttl("mykey")

        when:
        def result = redisService.memoizeList("mykey", 60) { [book1] }

        then:
        [book1] == result
        NO_EXPIRATION_TTL < redisService.ttl("mykey")
    }


    void testMemoizeSetWithoutRedis() {
        given:
        def book1 = "book1"
        def book2 = "book2"
        def book3 = "book3"
        def bookSet = [book1, book2, book3] as Set

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return bookSet
        }

        when:
        Set cacheMissSet = redisServiceMock.memoizeSet("mykey", cacheMissClosure)

        then:
        1 == calledCount
        [book1, book2, book3] as Set == cacheMissSet

        when:
        def cacheHitSet = redisServiceMock.memoizeSet("mykey", cacheMissClosure)

        then: "cache hit, don't call closure again"
        2 == calledCount
        [book1, book2, book3] as Set == cacheHitSet
        cacheMissSet == cacheHitSet
    }


    void testMemoizeSet() {
        given:
        def book1 = "book1"
        def book2 = "book2"
        def book3 = "book3"
        def bookSet = [book1, book2, book3] as Set

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return bookSet
        }

        when:
        Set cacheMissSet = redisService.memoizeSet("mykey", cacheMissClosure)

        then:
        1 == calledCount
        [book1, book2, book3] as Set == cacheMissSet
        NO_EXPIRATION_TTL == redisService.ttl("mykey")

        when:
        def cacheHitSet = redisService.memoizeSet("mykey", cacheMissClosure)

        then: "cache hit, don't call closure again"
        1 == calledCount
        [book1, book2, book3] as Set == cacheHitSet
        cacheMissSet == cacheHitSet
    }


    void testMemoizeSetWithExpire() {
        given:
        def book1 = "book1"
        assert 0 > redisService.ttl("mykey")

        when:
        def result = redisService.memoizeSet("mykey", 60) { [book1] as Set }

        then:
        [book1] as Set == result
        NO_EXPIRATION_TTL < redisService.ttl("mykey")
    }


    void testMemoizeObject_simpleMapOfStrings() {
        given:
        Map<String, String> map = [foo: "bar", baz: "qux"]

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            map
        }
        when:
        def cacheMissValue = redisService.memoizeObject(Map.class, "mykey", cacheMissClosure)
        then:
        1 == calledCount
        "bar" == cacheMissValue.foo
        "qux" == cacheMissValue.baz
        NO_EXPIRATION_TTL == redisService.ttl("mykey")

        when:
        def cacheHitValue = redisService.memoizeObject(Map.class, "mykey", cacheMissClosure)

        then:
        1 == calledCount
        "bar" == cacheHitValue.foo
        "qux" == cacheHitValue.baz
    }


    void testMemoizeObject_withTTL() {
        given:
        Map<String, String> map = [foo: "bar", baz: "qux"]
        assert 0 > redisService.ttl("mykey")

        when:
        def cacheMissValue = redisService.memoizeObject(Map.class, "mykey", 60) { -> map }

        then:
        "bar" == cacheMissValue.foo
        "qux" == cacheMissValue.baz
        NO_EXPIRATION_TTL < redisService.ttl("mykey")
    }


    void testMemoizeObject_nullValue() {
        given:
        Map<String, String> map = null

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            map
        }
        when:
        def cacheMissValue = redisService.memoizeObject(Map.class, "mykey", cacheMissClosure)
        then:
        1 == calledCount
        null == cacheMissValue
        when:
        def cacheHitValue = redisService.memoizeObject(Map.class, "mykey", cacheMissClosure)
        then:
        1 == calledCount
        null == cacheHitValue
    }


    void testMemoizeObject_nullValue_cacheNullFalse() {
        given:
        Map<String, String> map = null

        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            map
        }

        when:
        def cacheMissValue = redisService.memoizeObject(Map.class, "mykey", [cacheNull: false], cacheMissClosure)

        then:
        1 == calledCount
        null == cacheMissValue

        when:
        def cacheMissAgainValue = redisService.memoizeObject(Map.class, "mykey", [cacheNull: false], cacheMissClosure)

        then:
        2 == calledCount
        null == cacheMissAgainValue
    }


    void testDeleteKeysWithPattern() {
        given:
        def calledCount = 0
        def cacheMissClosure = {
            calledCount += 1
            return "foobar"
        }

        when:
        redisService.memoize("mykey:1", cacheMissClosure)
        redisService.memoize("mykey:2", cacheMissClosure)

        then:
        2 == calledCount

        when:
        redisService.memoize("mykey:1", cacheMissClosure)
        redisService.memoize("mykey:2", cacheMissClosure)

        then: "call count shouldn't increase"
        2 == calledCount

        when:
        redisService.deleteKeysWithPattern("mykey:*")

        redisService.memoize("mykey:1", cacheMissClosure)
        redisService.memoize("mykey:2", cacheMissClosure)

        then: "Because we deleted those keys before and there is a cache miss"
        4 == calledCount
    }


    void testWithTransaction() {
        given:
        def bar = ''

        when:
        redisService.withRedis { Jedis redis ->
            assert redis.get("foo") == null
            redisService.withTransaction { Transaction transaction ->
                transaction.set("foo", "bar")
                assert redis.get("foo") == null
            }
            bar = redis.get("foo")
        }

        then:
        bar == "bar"
    }


    void testWithTransactionClosureException() {
        given:
        def foo = "foo"
        def fooNew = "foo"
        redisService.withRedis { Jedis redis ->
            foo = redis.get("foo")
        }

        when:
        try {
            redisService.withTransaction { Transaction transaction ->
                transaction.set("foo", "bar")
                throw new Exception("Something bad happened")
            }
        } catch (Exception e) {
            assert e.message =~ /bad/
        }

        then:
        foo == null

        when:
        redisService.withRedis { Jedis redis ->
            fooNew = redis.get("foo")
        }

        then:
        fooNew == null
    }


    void testPropertyMissingGetterRetrievesStringValue() {
        given:
        assert redisService.foo == null

        when:
        redisService.withRedis { Jedis redis ->
            redis.set("foo", "bar")
        }

        then:
        "bar" == redisService.foo
    }


    void testPropertyMissingSetterSetsStringValue() {
        given:
        def bar = ""
        redisService.withRedis { Jedis redis ->
            assert redis.foo == null
        }

        when:
        redisService.foo = "bar"

        then:
        redisService.withRedis { Jedis redis ->
            bar = redis.foo
        }
        bar == "bar"
    }

    def testMethodMissingDelegatesToJedis() {
        given:
        assert redisService.foo == null

        when:
        redisService.set("foo", "bar")

        then:
        assert "bar" == redisService.foo
    }


    def testMethodNotOnJedisThrowsMethodMissingException() {
        when:
        def result = ""
        try {
            redisService.methodThatDoesNotExistAndNeverWill()
        } catch (Exception e) {
            result = e.message
        }

        then:
        result?.startsWith("No signature of method: redis.clients.jedis.Jedis.methodThatDoesNotExistAndNeverWill")
    }

    // utility method for assisting in test setup

    RedisService getNewInstanceOfBean(Class clazz) {
        String beanName = "prototype${clazz.name}"
        BeanBuilder beanBuilder = new BeanBuilder(grailsApplication.mainContext)

        beanBuilder.beans {
            "$beanName"(clazz) { bean ->
                bean.autowire = 'byName'
            }
        }

        beanBuilder.createApplicationContext().getBean(beanName)
    }

    def mockRedisServiceForFailureTest(RedisService svc) {
        def redisPoolMock = new Object()
        redisPoolMock.metaClass.getResource = { ->
            throw new JedisConnectionException('Generated by a mocked redisPool')
        }
        svc.redisPool = redisPoolMock
        return svc
    }

}
