package org.grails.gorm.rx.finders

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.rx.RxDatastoreClient
import org.grails.datastore.rx.query.RxQuery

/**
 * Implementation of findAllBy* dynamic finder for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class FindAllByFinder extends org.grails.datastore.gorm.finders.FindAllByFinder {
    final RxDatastoreClient datastoreClient

    FindAllByFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient.mappingContext)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected Object doInvokeInternal(DynamicFinderInvocation invocation) {
        def javaClass = invocation.getJavaClass()
        def query = datastoreClient.createQuery(javaClass)
        query = buildQuery(invocation, javaClass, query)
        def arguments = invocation.getArguments()
        if (arguments.length > 0 && (arguments[0] instanceof Map)) {
            ((RxQuery)query).findAll((Map)arguments[0])
        }
        else {
            return ((RxQuery)query).findAll()
        }

    }
}
