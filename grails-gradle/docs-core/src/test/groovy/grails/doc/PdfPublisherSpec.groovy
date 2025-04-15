package grails.doc

import spock.lang.Specification

class PdfPublisherSpec extends Specification {

    void "generate pdf from sample docs"() {
        given:
        String sampleDocsFolderPath = 'src/test/resources/docs'
        File sampleDocsFolder = new File(sampleDocsFolderPath)
        String pdfName = 'single.pdf'
        String child = "guide/single.html"

        expect:
        sampleDocsFolder.exists()
        !new File("${sampleDocsFolderPath}/guide/${pdfName}").exists()

        when:
        PdfPublisher.publishPdfFromHtml(sampleDocsFolder, child, pdfName)

        then:
        noExceptionThrown()
        new File("${sampleDocsFolderPath}/guide/${pdfName}").exists()

        cleanup:
        new File("${sampleDocsFolderPath}/guide/${pdfName}").delete()
    }
}
