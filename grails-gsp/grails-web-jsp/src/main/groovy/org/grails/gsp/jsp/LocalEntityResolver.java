/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp.jsp;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Resolves commons JSP DTDs and Schema definitions locally.
 *
 * @author Graeme Rocher
 */
public class LocalEntityResolver implements EntityResolver {

    private static final Map<String, String> ENTITIES = Map.ofEntries(
        // JSP taglib 3.0
        entry("https://jakarta.ee/xml/ns/jakartaee/web-jsptaglibrary_3_0.xsd", "web-jsptaglibrary_3_0.xsd"),
        // JSP taglib 2.1
        entry("http://java.sun.com/xml/ns/jee/web-jsptaglibrary_2_1.xsd", "web-jsptaglibrary_2_1.xsd"),
        // JSP taglib 2.0
        entry("http://java.sun.com/xml/ns/j2ee/web-jsptaglibrary_2_0.xsd", "web-jsptaglibrary_2_0.xsd"),
        // JSP taglib 1.2
        entry("-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN", "web-jsptaglibrary_1_2.dtd"),
        entry("http://java.sun.com/dtd/web-jsptaglibrary_1_2.dtd", "web-jsptaglibrary_1_2.dtd"),
        // JSP taglib 1.1
        entry("-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.1//EN", "web-jsptaglibrary_1_1.dtd"),
        entry("http://java.sun.com/j2ee/dtds/web-jsptaglibrary_1_1.dtd", "web-jsptaglibrary_1_1.dtd"),
        // Servlet 6.0
        entry("https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd", "web-app_6_0.xsd"),
        // Servlet 5.0
        entry("https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd", "web-app_5_0.xsd"),
        // Servlet 4.0
        entry("http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd", "web-app_4_0.xsd"),
        // Servlet 3.1
        entry("http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd", "web-app_3_1.xsd"),
        // Servlet 3.0
        entry("http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd", "web-app_3_0.xsd"),
        // Servlet 2.5
        entry("http://java.sun.com/xml/ns/jee/web-app_2_5.xsd", "web-app_2_5.xsd"),
        // Servlet 2.4
        entry("http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd", "web-app_2_4.xsd"),
        // Servlet 2.3
        entry("-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN", "web-app_2_3.dtd"),
        entry("http://java.sun.com/dtd/web-app_2_3.dtd", "web-app_2_3.dtd"),
        // Servlet 2.2
        entry("-//Sun Microsystems, Inc.//DTD Web Application 2.2//EN", "web-app_2_2.dtd"),
        entry("http://java.sun.com/j2ee/dtds/web-app_2_2.dtd", "web-app_2_2.dtd")
    );

    public InputSource resolveEntity(String publicId, String systemId) {
        String name = ENTITIES.get(publicId);

        if (name == null) name = ENTITIES.get(systemId);

        InputStream stream = name != null ? getClass().getResourceAsStream(name) :
            new ByteArrayInputStream(new byte[0]);

        InputSource is = new InputSource();
        is.setByteStream(stream);
        is.setPublicId(publicId);
        is.setSystemId(systemId);
        return is;
    }
}
