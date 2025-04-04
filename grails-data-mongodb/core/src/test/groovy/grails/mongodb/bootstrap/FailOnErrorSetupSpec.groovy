package grails.mongodb.bootstrap

import grails.gorm.tests.Plant
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.mongo.MongoDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 16/12/16.
 */
class FailOnErrorSetupSpec extends Specification {

    @Shared @AutoCleanup MongoDatastore datastore = new MongoDatastore([(Settings.SETTING_FAIL_ON_ERROR):true],Plant)


    void "test fail on error was configured correctly"() {

        when:
        def plant = new Plant()
        plant.save()

        then:
        plant.errors.hasErrors()
        thrown grails.validation.ValidationException
    }

}
