/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
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
