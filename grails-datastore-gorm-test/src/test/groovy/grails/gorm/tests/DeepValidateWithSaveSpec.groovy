package grails.gorm.tests

import grails.gorm.tck.TestEntity
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormValidateable
import spock.lang.Specification

/**
 * Created by graemerocher on 16/02/2017.
 */
class DeepValidateWithSaveSpec extends GormDatastoreSpec {

    void "test deep validate parameter"() {
        given:
        def validateable = Mock(GormValidateable)
        validateable.hasErrors() >> true
        def args = [deepValidate:true]

        when:
        GormInstanceApi instanceApi = GormEnhancer.findInstanceApi(TestEntity)
        instanceApi.save(validateable, [deepValidate:true])

        then:
        1 * validateable.validate(args)
    }
}
