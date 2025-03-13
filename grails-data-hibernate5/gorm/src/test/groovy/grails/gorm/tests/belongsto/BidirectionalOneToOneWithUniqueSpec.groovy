package grails.gorm.tests.belongsto

import org.grails.orm.hibernate.GormSpec

/**
 * Created by graemerocher on 22/08/2017.
 */
class BidirectionalOneToOneWithUniqueSpec extends GormSpec{


    void "test bidirectional one-to-one with unique"() {

        given:
        def nose = new Nose()
        def face = new Face(nose: nose)
        nose.face = face
        face.save(flush: true)
        session.clear()

        when:
        Face f = Face.first()

        then:
        f.nose
        f.nose.face
    }
    @Override
    List getDomainClasses() {
        [Face, Nose]
    }
}
