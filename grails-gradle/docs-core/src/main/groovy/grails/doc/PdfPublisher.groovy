package grails.doc

import groovy.transform.CompileStatic
import org.w3c.dom.Document

@CompileStatic
class PdfPublisher {

    static void publishPdfFromHtml(File outputDir, String child, String pdfName) {
        PdfBuilder pdfBuilder = new PdfBuilder()
        File currFile = new File(outputDir, child)
        String xml = pdfBuilder.createXml(currFile, outputDir.absolutePath)
        Document doc = pdfBuilder.createDocument(xml)
        File outputFile = new File(currFile.parentFile, pdfName)
        File urlBase = new File(outputDir, "guide/single.html")
        pdfBuilder.createPdfWithDocument(doc, outputFile, urlBase)
    }
}
