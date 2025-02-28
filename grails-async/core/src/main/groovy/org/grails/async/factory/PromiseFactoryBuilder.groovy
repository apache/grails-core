package org.grails.async.factory

import grails.async.PromiseFactory
import grails.async.Promises
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.async.factory.future.CachedThreadPoolPromiseFactory

/**
 * Constructs the default promise factory
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@CompileStatic
@Slf4j
class PromiseFactoryBuilder {

    /**
     * @return Builds the default PromiseFactory
     */
    static PromiseFactory build() {

        List<PromiseFactory> promiseFactories = ServiceLoader.load(PromiseFactory).toList()

        PromiseFactory promiseFactory
        if(promiseFactories.isEmpty()) {
            log.debug 'No PromiseFactory implementation found. Using default ExecutorService promise factory.'
            promiseFactory = new CachedThreadPoolPromiseFactory()
        }
        else {
            promiseFactory = promiseFactories.first()
            log.debug 'Found PromiseFactory implementation to use [{}]', promiseFactory
        }

        return promiseFactory
    }
}
