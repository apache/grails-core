package org.grails.gorm.rx.finders

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.rx.RxDatastoreClient

/**
 * Implementation of findBy* dynamic finder for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class FindByFinder extends org.grails.datastore.gorm.finders.FindByFinder {

    final RxDatastoreClient datastoreClient

    FindByFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient.mappingContext)
        this.datastoreClient = datastoreClient
    }

    @Override
    protected Object doInvokeInternal(DynamicFinderInvocation invocation) {
        def javaClass = invocation.getJavaClass()
        def query = datastoreClient.createQuery(javaClass)
        query = buildQuery(invocation, javaClass, query)
        invokeQuery(query)
    }
}
