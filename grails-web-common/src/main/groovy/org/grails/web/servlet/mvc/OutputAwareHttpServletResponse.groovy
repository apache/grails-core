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
package org.grails.web.servlet.mvc

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.http.HttpServletResponseWrapper

/**
 * Tracks whether getOutputStream() was called in order to prevent calls to getWriter() after
 * getOutputStream() has been called
 *
 *
 * @since 3.1.12
 * @author Graeme Rocher
 */
@CompileStatic
@InheritConstructors
class OutputAwareHttpServletResponse extends HttpServletResponseWrapper {

    /**
     * Whether the writer is available
     */
    boolean writerAvailable = true

    @Override
    PrintWriter getWriter() throws IOException {
        return super.getWriter()
    }

    @Override
    ServletOutputStream getOutputStream() throws IOException {
        writerAvailable = false
        return super.getOutputStream()
    }
}
