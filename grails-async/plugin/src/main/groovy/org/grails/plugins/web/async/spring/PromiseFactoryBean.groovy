package org.grails.plugins.web.async.spring

import grails.async.PromiseFactory
import grails.async.Promises
import groovy.transform.CompileStatic
import org.grails.async.factory.PromiseFactoryBuilder
import org.springframework.beans.factory.FactoryBean

/**
 * Factory bean for Spring integration
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
class PromiseFactoryBean extends PromiseFactoryBuilder implements FactoryBean<PromiseFactory> {
    @Override
    PromiseFactory getObject() throws Exception {
        PromiseFactory promiseFactory = build()
        Promises.setPromiseFactory(promiseFactory)
        return promiseFactory
    }

    @Override
    Class<?> getObjectType() {
        return PromiseFactory
    }

    @Override
    boolean isSingleton() {
        return true
    }

}
