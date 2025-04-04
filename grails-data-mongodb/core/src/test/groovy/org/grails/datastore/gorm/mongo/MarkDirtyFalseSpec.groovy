package org.grails.datastore.gorm.mongo

import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.config.MongoSettings

class MarkDirtyFalseSpec extends GormDatastoreSpec {

    Map getConfiguration() {
        [(MongoSettings.SETTING_MARK_DIRTY): false]
    }

    void "test behavior with mark dirty false"() {
        when:
        def b = new Bar(foo:"stuff", strings:['a', 'b'])
        b.save(flush:true)
        session.clear()
        b = Bar.get(b.id)
        b.save(flush: true)

        then:
        b.version == 0

        when:
        def bTs = new BarWithTimestamp(foo:"stuff")
        bTs.save(flush:true)
        session.clear()
        bTs = BarWithTimestamp.get(bTs.id)
        bTs.save(flush: true)

        then:
        bTs.version == 0
    }

    @Override
    List getDomainClasses() {
        [Bar, BarWithTimestamp]
    }
}

@Entity
@DirtyCheck
class BarWithTimestamp {
    ObjectId id

    String foo

    Date lastUpdated

}
