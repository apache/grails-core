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
package org.grails.web.errors

import org.grails.core.exceptions.DefaultErrorsPrinter
import org.grails.core.io.ResourceLocator
import org.grails.io.support.GrailsResourceUtils
import org.springframework.core.io.Resource

/**
 * Customized Stack trace output for the errors view.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class ErrorsViewStackTracePrinter extends DefaultErrorsPrinter {

    ErrorsViewStackTracePrinter(ResourceLocator resourceLocator) {
        super(resourceLocator)
    }

    @Override
    protected boolean shouldSkipNextCause(Throwable e) {
        return super.shouldSkipNextCause(e)
    }

    @Override
    String prettyPrint(Throwable t, Map attrs) {
        if (t instanceof GrailsWrappedRuntimeException) {
            t = t.cause
        }
        return super.prettyPrint(t, attrs)
    }

    @Override
    String prettyPrintCodeSnippet(Throwable t, Map attrs) {
        if (t instanceof GrailsWrappedRuntimeException) {
            t = t.cause
        }
        return super.prettyPrintCodeSnippet(t, attrs)
    }

    @Override
    String formatCodeSnippetStart(Resource resource, int lineNumber, Map attrs) {
        String path = resource.filename
        // try calc better path
        try {
            String abs = resource.file.absolutePath
            int i = abs.indexOf(GrailsResourceUtils.GRAILS_APP_DIR)
            if (i > -1) {
                path = abs[i..-1]
            }
        } catch (e) {
            path = resource.filename
        }
        """\
<h2>Around line ${lineNumber} of <span class="${attrs['filenameClass'] ?: 'filename'}">${path}</span></h2>
<pre class="${attrs['snippetClass'] ?: 'snippet'}">"""
    }

    @Override
    String formatCodeSnippetEnd(Resource resource, int lineNumber) {
        "</pre>"
    }

    @Override
    protected String formatCodeSnippetLine(int currentLineNumber, Object currentLine, Map attrs) {
        """<code class="${attrs['lineClass'] ?: 'line'}"><span class="${attrs['lineNumberClass'] ?: 'lineNumber'}">${currentLineNumber}:</span>${currentLine.encodeAsHTML()}</code>"""
    }

    @Override
    protected String formatCodeSnippetErrorLine(int currentLineNumber, Object currentLine, Map attrs) {
        """<code class="${attrs['lineErrorClass'] ?: 'line error'}"><span class="${attrs['lineNumberClass'] ?: 'lineNumber'}">${currentLineNumber}:</span>${currentLine.encodeAsHTML()}</code>"""
    }

    @Override
    protected int getLineNumberInfo(Throwable cause, int defaultInfo) {
        return super.getLineNumberInfo(cause, defaultInfo)
    }
}
