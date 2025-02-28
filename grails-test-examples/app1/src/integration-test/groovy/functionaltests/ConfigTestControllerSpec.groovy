package functionaltests


import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
@Rollback
class ConfigTestControllerSpec extends ContainerGebSpec {

    void "Test that configuration properties are correctly read"() {
        when:"When evaluting configuration values"
        go '/configTest/index'

        then:"The values are correct"
        $('div', 0).text() == 'test'
        $('div', 1).text() == '1'
        $('div', 2).text() == 'test'
        $('div', 3).text() == '1'
        $('div', 4).text() == 'test'
        $('div', 5).text() == '1'
        String text = $('div', 6).text()
        text.contains('baz=1')
        text.contains('bax=2')
        text.contains('bar=test')
    }
}
