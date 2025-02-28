package hyphenated

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
@Rollback
class HyphenateControllerSpec extends ContainerGebSpec {

    void "test something"() {
        when:"The home page is visited"
            go '/hyphenate/long-name'

        then:"The title is correct"
        	$().text() == 'good'
    }
}
