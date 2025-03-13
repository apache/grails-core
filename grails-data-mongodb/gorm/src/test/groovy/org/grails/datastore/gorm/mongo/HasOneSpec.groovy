package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity

/**
 * Tests hasOne functionality with MongoDB.
 */
class HasOneSpec extends GormDatastoreSpec {

    void "Test that a hasOne association is persisted correctly"() {
        when:"A hasOne association is created and persisted"
            final nose = new Nose(isLong: true)
            def f = new Face(name:"Bob", nose: nose)
            nose.face = f
            f.save flush:true

            session.clear()
            f = Face.get(f.id)
            def fdbo = Face.collection.find().first()
            def ndbo = Nose.collection.find().first()

        then:"The data is persisted correctly"
            f.nose != null
            f.nose.face != null
            f.name == "Bob"
            f.nose.isLong
            fdbo.name == "Bob"
            fdbo.nose == null
            ndbo.isLong == true
            ndbo.face != null

        when:"A hasOne is updated"
            f.name = "Fred"
            f.nose.isLong = false
            f.save(flush:true)
            session.clear()
            f = Face.get(f.id)
        then:"The data is persisted correctly"
            f.nose != null
            f.nose.face != null
            f.name == "Fred"
            !f.nose.isLong

        when:"The owner is deleted"
            f.delete flush:true

        then:"The child is gone too"
            Face.count() == 0
            Nose.count() == 0
    }

    @Override
    List getDomainClasses() {
        [Face, Nose]
    }
}

@Entity
class Face {
    String id
    String name
    Nose nose
    static hasOne = [nose:Nose]
}

@Entity
class Nose {
    String id
    boolean isLong
    Face face
    static belongsTo = [face:Face]
}
