package org.demo.spock

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import org.demo.spock.pages.UploadPage
import spock.lang.IgnoreIf
import spock.lang.Requires

/**
 * Altered copy of {@link UploadSpec} that depends on default of
 * {@link grails.plugin.geb.ContainerGebConfiguration#fileDetector} to be
 * {@link org.openqa.selenium.remote.LocalFileDetector}
 */
@Integration
class ContainerFileDetectorDefaultSpec extends ContainerGebSpec {

    void 'should be able to find and upload local files'() {
        given:
        def uploadPage = to UploadPage

        when:
        uploadPage.fileInput.file = new File('src/integration-test/resources/assets/upload-test.txt')

        and:
        uploadPage.submitBtn.click()

        then:
        title == 'File Uploaded'
        pageSource.contains('File uploaded successfully')
    }
}