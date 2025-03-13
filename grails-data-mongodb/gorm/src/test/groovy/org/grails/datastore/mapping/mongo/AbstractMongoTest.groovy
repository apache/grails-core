package org.grails.datastore.mapping.mongo

import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.junit.Before
import org.springframework.context.support.GenericApplicationContext

abstract class AbstractMongoTest {

    protected MongoDatastore md

    @Before
    void setUp() {
        md = new MongoDatastore(new MongoMappingContext(getClass().simpleName))
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        md.applicationContext = ctx
        md.afterPropertiesSet()
    }
}
