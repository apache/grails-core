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
package org.grails.gsp.jsp

import org.grails.web.taglib.jsp.JspLocaleSelectTag
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse


class TldReaderTests {

    @Test
    void testTldReader() {
        def res = new ClassPathResource("org/codehaus/groovy/grails/web/pages/ext/jsp/tld-reader-test.tld")

        TldReader tldReader = new TldReader(res.getInputStream())

        assert tldReader.tags
        assertEquals tldReader.tags.localeSelect, JspLocaleSelectTag.class.name
    }

    /**
     * JSP 1.2 descriptors declare a DOCTYPE. JSTL ships several, among them {@code c-1_0-rt.tld},
     * which the default {@code grails.gsp.tldScanPattern} scans, so the reader has to accept one.
     */
    @Test
    void testTldReaderAcceptsDescriptorDeclaringDoctype() {
        def tld = '''<?xml version="1.0" encoding="ISO-8859-1" ?>
<!DOCTYPE taglib
  PUBLIC "-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN"
  "http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd">
<taglib>
  <tlib-version>1.0</tlib-version>
  <jsp-version>1.2</jsp-version>
  <short-name>c_rt</short-name>
  <uri>http://java.sun.com/jstl/core_rt</uri>
  <tag>
    <name>out</name>
    <tag-class>org.apache.taglibs.standard.tag.rt.core.OutTag</tag-class>
    <body-content>JSP</body-content>
  </tag>
</taglib>'''

        TldReader tldReader = new TldReader(new ByteArrayInputStream(tld.getBytes('ISO-8859-1')))

        assertEquals 'http://java.sun.com/jstl/core_rt', tldReader.uri
        assertEquals 'org.apache.taglibs.standard.tag.rt.core.OutTag', tldReader.tags.out
    }

    /**
     * Accepting the declaration must not reopen the XXE vector: an entity pointing at a file on
     * disk contributes nothing to the descriptor.
     */
    @Test
    void testTldReaderDoesNotResolveExternalEntities() {
        File secret = File.createTempFile('tld-reader-secret', '.txt')
        try {
            secret.text = 'top-secret-token'
            def tld = """<!DOCTYPE taglib [
<!ENTITY ext SYSTEM '${secret.toURI().toASCIIString()}'>
]>
<taglib>
  <uri>&ext;</uri>
  <tag><name>out</name><tag-class>org.example.OutTag</tag-class></tag>
</taglib>"""

            TldReader tldReader = new TldReader(new ByteArrayInputStream(tld.getBytes('UTF-8')))

            assertFalse tldReader.uri.contains('top-secret-token')
            assertEquals 'org.example.OutTag', tldReader.tags.out
        }
        finally {
            secret.delete()
        }
    }
}
