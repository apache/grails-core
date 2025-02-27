package org.demo.spock

import grails.plugin.geb.ContainerGebConfiguration
import grails.plugin.geb.ContainerGebSpec
import grails.plugin.geb.UselessContainerFileDetector
import grails.testing.mixin.integration.Integration
import org.demo.spock.pages.UploadPage
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.remote.UselessFileDetector
import spock.lang.IgnoreIf
import spock.lang.Requires

/**
 * Altered copy of {@link LocalUploadSpec} that throws {@link org.openqa.selenium.InvalidArgumentException}
 */
@Integration
@ContainerGebConfiguration(fileDetector = UselessContainerFileDetector)
class UselessUploadSpec extends ContainerGebSpec {

    @Requires({ os.windows })
    void 'should be able to find and upload files on a Windows host'() {
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

    @IgnoreIf({ os.windows })
    void 'should be able to find and upload files on a non-Windows host'() {
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