package functionaltests

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import org.testcontainers.images.builder.Transferable

/**
 */
@Integration(applicationClass = Application)
class UploadControllerSpec extends ContainerGebSpec {

    void "Test file upload"() {
        when:"When go to an upload page"
        go "/upload/index"

        getContainer().copyFileToContainer(Transferable.of("Test upload", 0777), "/test.txt")
        def form = $('#myForm')

        form.myFile = "/test.txt"
        $('#input1').click()

        then:"The file is uploaded"
        $('p').text() == 'Test upload'
    }

    void "Test file upload parameters"() {
        when:"When go to an upload page"
        go "/upload/index"

        getContainer().copyFileToContainer(Transferable.of("Test upload", 0777), "/test.txt")
        def form = $('#myForm2')

        form.myFile = "/test.txt"
        $('#input2').click()

        then:"The file is uploaded"
        $('p').text() == 'ok'

    }
}
