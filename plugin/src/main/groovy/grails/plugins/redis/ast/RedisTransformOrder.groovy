package grails.plugins.redis.ast

import org.apache.grails.common.compiler.GroovyTransformOrder

interface RedisTransformOrder {
    int MEMOIZE_ORDER = GroovyTransformOrder.RX_SCHEDULER_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_DOMAIN_LIST_ORDER = RedisTransformOrder.MEMOIZE_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_DOMAIN_OBJECT_ORDER = RedisTransformOrder.MEMOIZE_DOMAIN_LIST_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_HASH_ORDER = RedisTransformOrder.MEMOIZE_DOMAIN_OBJECT_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_HASH_FIELD_ORDER = RedisTransformOrder.MEMOIZE_HASH_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_LIST_ORDER = RedisTransformOrder.MEMOIZE_HASH_FIELD_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_OBJECT_ORDER = RedisTransformOrder.MEMOIZE_LIST_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
    int MEMOIZE_SCORE_ORDER = RedisTransformOrder.MEMOIZE_OBJECT_ORDER + GroovyTransformOrder.DECREMENT_PRIORITY
}