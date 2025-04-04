package org.grails.datastore.gorm

import org.grails.datastore.gorm.support.BeforeValidateHelper
import spock.lang.Specification


class BeforeValidateHelperSerializationSpec extends Specification {

    void "test serializing an instance of BeforeValidateHelper"() {
        given:
        def helper = new BeforeValidateHelper()

        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)

        when:
        oos.writeObject(helper)
        oos.flush()

        def bis = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bis)
        def helper2 = ois.readObject()

        then:
        helper2 instanceof BeforeValidateHelper
    }

    void "test serializing an instance which references BeforeValidateHelper"() {
        given:
        def obj = new SomethingThatReferencesBeforeValidateHelper(firstName: 'Jeff', lastName: 'Brown')

        def bos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(bos)

        when:
        oos.writeObject(obj)
        oos.flush()

        def bis = new ByteArrayInputStream(bos.toByteArray())
        def ois = new ObjectInputStream(bis)
        def obj2 = ois.readObject()

        then:
        obj2 instanceof SomethingThatReferencesBeforeValidateHelper
        obj2.firstName == 'Jeff'
        obj2.lastName == 'Brown'
    }
}

class SomethingThatReferencesBeforeValidateHelper implements Serializable {
    String firstName
    String lastName
    private BeforeValidateHelper helper = new BeforeValidateHelper()
}
