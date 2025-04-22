package grails.doc

import spock.lang.Specification

class PdfPublisherSpec extends Specification {

    void "generate pdf from sample docs"() {
        given:
        File documentationFolder = new File('src/test/resources/docs')
        File outputDir = new File(documentationFolder, 'guide')
        File targetFile = new File(documentationFolder, 'guide/single.html')
        String pdfName = 'single.pdf'
        File pdfFile = new File(documentationFolder, "guide/${pdfName}")

        expect:
        documentationFolder.exists()
        !pdfFile.exists()

        when:
        PdfPublisher.publishPdfFromHtml(outputDir, targetFile, pdfName)

        then:
        noExceptionThrown()
        pdfFile.exists()

        cleanup:
        pdfFile.delete()
    }
}
