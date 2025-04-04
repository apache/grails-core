package org.grails.gorm.rx.finders

import groovy.transform.CompileStatic
import org.grails.datastore.rx.RxDatastoreClient

/**
 * Created by graemerocher on 06/05/16.
 */
@CompileStatic
class FindOrSaveByFinder extends FindOrCreateByFinder {
    FindOrSaveByFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient)
        setPattern(org.grails.datastore.gorm.finders.FindOrSaveByFinder.METHOD_PATTERN)
    }

    @Override
    protected boolean shouldSaveOnCreate() {
        return true;
    }
}
