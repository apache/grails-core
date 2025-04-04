package org.grails.gorm.rx.finders

import groovy.transform.CompileStatic
import org.grails.datastore.rx.RxDatastoreClient

/**
 * Implementation of findBy* boolean finder for RxGORM
 *
 * @see org.grails.datastore.gorm.finders.FindByBooleanFinder
 */
@CompileStatic
class FindByBooleanFinder extends FindByFinder {
    FindByBooleanFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient)
        setPattern(org.grails.datastore.gorm.finders.FindByBooleanFinder.METHOD_PATTERN)
    }

    @Override
    public boolean firstExpressionIsRequiredBoolean() {
        return true;
    }
}
