package org.grails.gorm.rx.finders

import groovy.transform.CompileStatic
import org.grails.datastore.rx.RxDatastoreClient

/**
 * Implementation of findAllBy* boolean finder for RxGORM
 *
 * @see org.grails.datastore.gorm.finders.FindAllByBooleanFinder
 */
@CompileStatic
class FindAllByBooleanFinder extends FindAllByFinder {
    FindAllByBooleanFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient)
        setPattern(org.grails.datastore.gorm.finders.FindAllByBooleanFinder.METHOD_PATTERN)
    }

    @Override
    public boolean firstExpressionIsRequiredBoolean() {
        return true;
    }
}
