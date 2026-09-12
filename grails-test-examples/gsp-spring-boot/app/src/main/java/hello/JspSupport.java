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
package hello;

import java.net.MalformedURLException;

import jakarta.servlet.ServletContext;

/**
 * Whether this application can serve the JSP half of the example.
 *
 * <p>Jasper compiles a JSP from the servlet context, which carries {@code src/main/webapp} in a war
 * and in a run from the project directory, and nothing in an executable jar, where the JSP is not
 * packaged at all. The JSP libraries are on the class path either way - the GSP form uses the
 * Spring form tag library, which is a JSP tag library - so what settles it is whether the page is
 * there to serve. An executable jar does have a document root, a temporary and empty one, so
 * {@code getRealPath} answers this question wrongly.
 */
final class JspSupport {

    /** The JSP rendering of the form, served from the servlet context rather than the class path. */
    static final String JSP_VIEW = "/form.jsp";

    private JspSupport() {
    }

    static boolean canServeJsp(ServletContext servletContext) {
        if (servletContext == null) {
            return false;
        }
        try {
            return servletContext.getResource(JSP_VIEW) != null;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
