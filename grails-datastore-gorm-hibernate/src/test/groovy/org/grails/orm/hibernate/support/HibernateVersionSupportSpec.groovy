package org.grails.orm.hibernate.support

import org.hibernate.Version
import spock.lang.Specification

/**
 * Created by graemerocher on 04/04/2017.
 */
class HibernateVersionSupportSpec extends Specification {

    void 'test hibernate version is at least'() {
        expect:
        Version.getVersionString() > "6.0.0"

    }
}
