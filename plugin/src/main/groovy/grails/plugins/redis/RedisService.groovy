/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.redis

import com.google.gson.Gson
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import redis.clients.jedis.Jedis
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.exceptions.JedisConnectionException

@Slf4j
class RedisService {

    public static final int NO_EXPIRATION_TTL = -1
    public static final int KEY_DOES_NOT_EXIST = -2  // added in redis 2.8

    def redisPool
    GrailsApplication grailsApplication

    boolean transactional = false

    RedisService withConnection(String connectionName) {
        if (grailsApplication.mainContext.containsBean("redisService${connectionName.capitalize()}")) {
            return (RedisService) grailsApplication.mainContext.getBean("redisService${connectionName.capitalize()}")
        }
        log.error('Connection with name redisService{} could not be found, returning default redis instead', connectionName.capitalize())
        return this
    }

    def withPipeline(Closure closure, Boolean returnAll = false) {
        withRedis { Jedis redis ->
            Pipeline pipeline = redis.pipelined()
            closure(pipeline)
            returnAll ? pipeline.syncAndReturnAll() : pipeline.sync()
        }
    }

    def withOptionalPipeline(Closure clos, Boolean returnAll = false) {
        withOptionalRedis { Jedis redis ->
            if (redis) {
                Pipeline pipeline = redis.pipelined()
                clos(pipeline)
                returnAll ? pipeline.syncAndReturnAll() : pipeline.sync()
            } else {
                return clos()
            }

        }
    }

    def withTransaction(Closure closure) {
        withRedis { Jedis redis ->
            Transaction transaction = redis.multi()
            try {
                closure(transaction)
            } catch (Exception exception) {
                transaction.discard()
                throw exception
            }

            transaction.exec()
        }
    }

    def methodMissing(String name, args) {
        log.debug('methodMissing {}', name)
        withRedis { Jedis redis ->
            redis.invokeMethod(name, args)
        }
    }

    void propertyMissing(String name, Object value) {
        withRedis { Jedis redis ->
            redis.set(name, value.toString())
        }
    }

    Object propertyMissing(String name) {
        withRedis { Jedis redis ->
            redis.get(name)
        }
    }

    def withRedis(Closure closure) {
        Jedis redis = redisPool.resource
        try {
            return closure(redis)
        } catch (JedisConnectionException jce) {
            throw jce
        } catch (Exception e) {
            throw e
        } finally {
            if (redis) {
                redis.close()
            }
        }
    }

    /**
     * An implementation of withRedis that suppresses JedisConnectException to support the memoization model
     * @param clos
     * @return
     */
    def withOptionalRedis(Closure clos) {
        Jedis redis
        try {
            redis = redisPool.resource
        }
        catch (JedisConnectionException jce) {
            log.info('Unreachable redis store trying to retrieve redis resource. Please check redis server and/or config!')
        }

        try {
            return clos(redis)
        } catch (JedisConnectionException jce) {
            log.error('Unreachable redis store trying to return redis pool resource. Please check redis server and/or config!', jce)
        } catch (Throwable t) {
            throw t
        } finally {
            if (redis) {
                redis.close()
            }
        }
    }

    def memoize(String key, Integer expire, Closure closure) {
        memoize(key, [expire: expire], closure)
    }

    // SET/GET a value on a Redis key
    def memoize(String key, Map options = [:], Closure closure) {
        log.debug('using key {}', key)
        def result = withOptionalRedis { Jedis redis ->
            if (redis) return redis.get(key)
        }

        if (!result) {
            log.debug('cache miss: {}', key)
            result = closure()
            if (result) withOptionalRedis { Jedis redis ->
                if (redis) {
                    if (options?.expire) {
                        redis.setex(key, options.expire, result as String)
                    } else {
                        redis.set(key, result as String)
                    }
                }
            }
        } else {
            log.debug('cache hit : {} = {}', key, result)
        }
        result
    }

    def memoizeHash(String key, Integer expire, Closure closure) {
        memoizeHash(key, [expire: expire], closure)
    }

    def memoizeHash(String key, Map options = [:], Closure closure) {
        def hash = withOptionalRedis { Jedis redis ->
            if (redis) return redis.hgetAll(key)
        }

        if (!hash) {
            log.debug('cache miss: {}', key)
            hash = closure()
            if (hash) withOptionalRedis { Jedis redis ->
                if (redis) {
                    redis.hmset(key, hash)
                    if (options?.expire) redis.expire(key, options.expire)
                }
            }
        } else {
            log.debug('cache hit : {} = {}', key, hash)
        }
        hash
    }

    def memoizeHashField(String key, String field, Integer expire, Closure closure) {
        memoizeHashField(key, field, [expire: expire], closure)
    }

    // HSET/HGET a value on a Redis hash at key.field
    // if expire is not null it will be the expire for the whole hash, not this value
    // and will only be set if there isn't already a TTL on the hash
    def memoizeHashField(String key, String field, Map options = [:], Closure closure) {
        def result = withOptionalRedis { Jedis redis ->
            if (redis) return redis.hget(key, field)
        }

        if (!result) {
            log.debug('cache miss: {}.{}', key, field)
            result = closure()
            if (result) withOptionalRedis { Jedis redis ->
                if (redis) {
                    redis.hset(key, field, result as String)
                    if (options?.expire && redis.ttl(key) == NO_EXPIRATION_TTL) redis.expire(key, options.expire)
                }
            }
        } else {
            log.debug('cache hit : {}.{} = {}', key, field, result)
        }
        result
    }

    def memoizeScore(String key, String member, Integer expire, Closure closure) {
        memoizeScore(key, member, [expire: expire], closure)
    }

    // set/get a 'double' score within a sorted set
    // if expire is not null it will be the expire for the whole zset, not this value
    // and will only be set if there isn't already a TTL on the zset
    def memoizeScore(String key, String member, Map options = [:], Closure closure) {
        def score = withOptionalRedis { Jedis redis ->
            if (redis) redis.zscore(key, member)
        }

        if (!score) {
            log.debug('cache miss: {}.{}', key, member)
            score = closure()
            if (score) withOptionalRedis { Jedis redis ->
                if (redis) {
                    redis.zadd(key, score, member)
                    if (options?.expire && redis.ttl(key) == NO_EXPIRATION_TTL) redis.expire(key, options.expire)
                }
            }
        } else {
            log.debug('cache hit : {}.{} = {}', key, member, score)
        }
        score
    }

    List memoizeDomainList(Class domainClass, String key, Integer expire, Closure closure) {
        memoizeDomainList(domainClass, key, [expire: expire], closure)
    }

    List memoizeDomainList(Class domainClass, String key, Map options = [:], Closure closure) {
        List<Long> idList = getIdListFor(key)
        if (idList) return hydrateDomainObjectsFrom(domainClass, idList)

        def domainList = withOptionalRedis { Jedis redis ->
            closure(redis)
        }

        saveIdListTo(key, domainList, options.expire)

        domainList
    }

    List<Long> memoizeDomainIdList(Class domainClass, String key, Integer expire, Closure closure) {
        memoizeDomainIdList(domainClass, key, [expire: expire], closure)
    }

    // used when we just want the list of Ids back rather than hydrated objects
    List<Long> memoizeDomainIdList(Class domainClass, String key, Map options = [:], Closure closure) {
        List<Long> idList = getIdListFor(key)
        if (idList) return idList

        def domainList = closure()

        saveIdListTo(key, domainList, options.expire)

        getIdListFor(key)
    }

    protected List<Long> getIdListFor(String key) {
        List<String> idList = withOptionalRedis { Jedis redis ->
            if (redis) return redis.lrange(key, 0, -1)
        }

        if (idList) {
            log.debug('{} cache hit, returning {} ids', key, idList.size())
            List<Long> idLongList = idList*.toLong()
            return idLongList
        }
    }

    protected void saveIdListTo(String key, List domainList, Integer expire = null) {
        log.debug('{} cache miss, memoizing {} ids', key, domainList?.size() ?: 0)
        withOptionalPipeline { pipeline ->
            if (pipeline) {
                for (domain in domainList) {
                    pipeline.rpush(key, domain.id as String)
                }
                if (expire) pipeline.expire(key, expire)
            }
        }
    }

    @Transactional(readOnly = true)
    protected List hydrateDomainObjectsFrom(Class domainClass, List<Long> idList) {
        if (domainClass && idList) {
            //return domainClass.findAllByIdInList(idList, [cache: true])
            return idList.collect { id -> domainClass.load(id) }
        }
        []
    }

    def memoizeDomainObject(Class domainClass, String key, Integer expire, Closure closure) {
        memoizeDomainObject(domainClass, key, [expire: expire], closure)
    }

    // closure can return either a domain object or a Long id of a domain object
    // it will be persisted into redis as the Long
    @Transactional(readOnly = true)
    def memoizeDomainObject(Class domainClass, String key, Map options = [:], Closure closure) {
        Long domainId = withOptionalRedis { redis ->
            redis?.get(key)?.toLong()
        }
        if (!domainId) domainId = persistDomainId(closure()?.id as Long, key, options.expire)
        domainClass.load(domainId)
    }

    Long persistDomainId(Long domainId, String key, Integer expire) {
        if (domainId) {
            withOptionalPipeline { pipeline ->
                if (pipeline) {
                    pipeline.set(key, domainId.toString())
                    if (expire) pipeline.expire(key, expire)
                }
            }
        }
        domainId
    }

    def memoizeObject(Class clazz, String key, Integer expire, Closure closure) {
        memoizeObject(clazz, key, [expire: expire], closure)
    }

    def memoizeObject(Class clazz, String key, Map options = [:], Closure closure) {
        Gson gson = new Gson()

        String memoizedJson = memoize(key, options) { ->
            def original = closure()
            if (original == null && options.cacheNull == false) return null
            gson.toJson(original)
        }

        gson.fromJson((String) memoizedJson, clazz)
    }

    // deletes all keys matching a pattern (see redis "keys" documentation for more)
    // OK for low traffic methods, but expensive compared to other redis commands
    // perf test before relying on this rather than storing your own set of keys to 
    // delete
    void deleteKeysWithPattern(keyPattern) {
        log.info('Cleaning all redis keys with pattern [{}]', keyPattern.toString())
        withRedis { Jedis redis ->
            String[] keys = redis.keys(keyPattern)
            if (keys) redis.del(keys)
        }
    }

    /**
     * Deletes key from redis.
     *
     * @param key The key to delete.
     */
    void deleteKey(String key){
        withRedis { Jedis redis ->
            redis.del(key)
        }
    }

    def memoizeList(String key, Integer expire, Closure closure) {
        memoizeList(key, [expire: expire], closure)
    }

    def memoizeList(String key, Map options = [:], Closure closure) {
        List list = withOptionalRedis { Jedis redis ->
            if (redis) return redis.lrange(key, 0, -1)
        }

        if (!list) {
            log.debug('cache miss: {}', key)
            list = closure()
            if (list) withOptionalPipeline { pipeline ->
                if (pipeline) {
                    for (obj in list) {
                        pipeline.rpush(key, obj)
                    }
                    if (options?.expire) pipeline.expire(key, options.expire)
                }
            }
        } else {
            log.debug('cache hit: {}', key)
        }
        list
    }

    def memoizeSet(String key, Integer expire, Closure closure) {
        memoizeSet(key, [expire: expire], closure)
    }

    def memoizeSet(String key, Map options = [:], Closure closure) {
        def set = withOptionalRedis { Jedis redis ->
            if (redis) return redis.smembers(key)
        }

        if (!set) {
            log.debug('cache miss: {}', key)
            set = closure()
            if (set) withOptionalPipeline { pipeline ->
                if (pipeline) {
                    for (obj in set) {
                        pipeline.sadd(key, obj)
                    }
                    if (options?.expire) pipeline.expire(key, options.expire)
                }
            }
        } else {
            log.debug('cache hit: {}', key)
        }
        set
    }
    // should ONLY Be used from tests unless we have a really good reason to clear out the entire redis db
    def flushDB() {
        log.warn('flushDB called!')
        withRedis { Jedis redis ->
            redis.flushDB()
        }
    }
}
