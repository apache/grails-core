package org.grails.orm.hibernate

import org.grails.orm.hibernate.cfg.Settings
import spock.lang.Specification

/**
 * Created by graemerocher on 22/09/2016.
 */
class HibernateDatastoreSpec extends Specification {

    void "test configure via map"() {
        when:"The map constructor is used"
        def config = Collections.singletonMap(Settings.SETTING_DB_CREATE,  "create-drop")
        HibernateDatastore datastore = new HibernateDatastore(config, Book)

        then:"GORM is configured correctly"
        Book.withNewSession {
            Book.count()
        } == 0
    }
}
