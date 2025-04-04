package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec

/**
 * Tests the unique constraint
 */

class UniqueConstraintSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [UniqueGroup, GroupWithin]
    }
}
