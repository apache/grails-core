package org.demo.spock

import grails.plugin.geb.ContainerGebConfiguration
import grails.plugin.geb.ContainerGebSpec
import grails.plugin.geb.UselessContainerFileDetector
import grails.testing.mixin.integration.Integration
import org.demo.spock.pages.UploadPage
import org.openqa.selenium.WebDriverException

/**
 * Altered copy of {@link ContainerFileDetectorDefaultSpec} 
 * that throws {@link org.openqa.selenium.InvalidArgumentException}
 */
@Integration
@ContainerGebConfiguration(fileDetector = UselessContainerFileDetector)
class ContainerFileDetectorAnnotationSpec extends ContainerGebSpec {

    void 'should fail to find file with fileDetector changed to UselessContainerFileDetector via annotation'() {
        given:
        def uploadPage = to UploadPage

        when:
        uploadPage.fileInput.file = new File('src/integration-test/resources/assets/upload-test.txt')

        and:
        uploadPage.submitBtn.click()

        then:
        def e = thrown(WebDriverException)
        e.message.contains('File not found')
    }
}