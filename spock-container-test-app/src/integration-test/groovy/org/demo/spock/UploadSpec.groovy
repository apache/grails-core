package org.demo.spock

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import org.demo.spock.pages.UploadPage
import spock.lang.IgnoreIf
import spock.lang.Requires

@Integration
class UploadSpec extends ContainerGebSpec {

    @Requires({ os.windows })
    void 'should be able to upload files on a Windows host'() {
        given:
        to UploadPage

        when:
        fileInput.file = createFileInputSource(
                'src/integration-test/resources/assets/upload-test.txt',
                '/tmp/upload-test.txt'
        )

        and:
        submitBtn.click()

        then:
        title == 'File Uploaded'
        browser.pageSource.contains('File uploaded successfully')
    }

    @IgnoreIf({ os.windows })
    void 'should be able to upload files on a non-Windows host'() {
        given:
        to UploadPage

        when:
        fileInput.file = createFileInputSource(
                'src/integration-test/resources/assets/upload-test.txt',
                '/tmp/upload-test.txt'
        )

        and:
        submitBtn.click()

        then:
        title == 'File Uploaded'
        browser.driver.pageSource.contains('File uploaded successfully')
    }
}