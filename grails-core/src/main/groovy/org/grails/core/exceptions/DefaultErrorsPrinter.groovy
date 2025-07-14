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
package org.grails.core.exceptions

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.grails.core.io.ResourceLocator
import org.grails.exceptions.reporting.CodeSnippetPrinter
import org.grails.exceptions.reporting.DefaultStackTracePrinter
import org.grails.exceptions.reporting.SourceCodeAware
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource

/**
 * Default implementation of the {@link StackTracePrinter} interface.
 *
 * @since 2.0
 *
 * @author Graeme Rocher
 * @author Marc Palmer
 */
class DefaultErrorsPrinter extends DefaultStackTracePrinter implements CodeSnippetPrinter {

    ResourceLocator resourceLocator

    DefaultErrorsPrinter() {}

    DefaultErrorsPrinter(ResourceLocator resourceLocator) {
        this.resourceLocator = resourceLocator
    }

    protected String getFileName(StackTraceElement te) {
        final res = resourceLocator?.findResourceForClassName(te.className)
        res == null ? te.className : res.getFilename()
    }

    @Deprecated
    String prettyPrintCodeSnippet(Throwable exception) {
        prettyPrintCodeSnippet(exception, [:])
    }

    String prettyPrintCodeSnippet(Throwable exception, Map attrs) {
        if (exception == null) {
            return ''
        }

        def sw = new StringWriter()
        def pw = new PrintWriter(sw)
        def lineNumbersShown = [:].withDefault { k -> [] }

        Throwable cause = exception
        while (cause) {

            if (!cause.stackTrace) break
            if (cause.getClass().name == 'org.springframework.web.util.NestedServletException') {
                cause = cause.cause
                continue
            }

            boolean first = true
            for (entry in cause.stackTrace) {
                Resource res
                String className = entry.className
                int lineNumber = getLineNumberInfo(cause, entry.lineNumber)
                if (first) {
                    res = getFileNameInfo(cause, res)
                    if (res != null) {
                        first = false
                    }
                }

                if (!className || !lineNumber) {
                    continue
                }

                res = res ?: resourceLocator?.findResourceForClassName(className)
                if (res != null) {
                    if (lineNumbersShown[res.filename].contains(lineNumber)) continue
                    // don't repeat the same lines twice

                    lineNumbersShown[res.filename] << lineNumber
                    pw.print formatCodeSnippetStart(res, lineNumber, attrs)
                    def input = null
                    try {
                        input = res.inputStream
                        input.withReader { fileIn ->
                            def reader = new LineNumberReader(fileIn)
                            int last = lineNumber + 3
                            def range = (lineNumber - 3..last)
                            String currentLine = reader.readLine()

                            while (currentLine != null) {
                                int currentLineNumber = reader.lineNumber
                                if (currentLineNumber in range) {
                                    boolean isErrorLine = currentLineNumber == lineNumber
                                    if (isErrorLine) {
                                        pw.print formatCodeSnippetErrorLine(currentLineNumber, currentLine, attrs)
                                    } else {
                                        pw.print formatCodeSnippetLine(currentLineNumber, currentLine, attrs)
                                    }
                                } else if (currentLineNumber > last) {
                                    break
                                }

                                currentLine = reader.readLine()
                            }
                        }
                    }
                    catch (e) {
                        // ignore
                    }
                    finally {
                        try {
                            input?.close()
                        } catch (e) {
                            // ignore
                        }
                        pw.print formatCodeSnippetEnd(res, lineNumber)
                    }
                } else {
                    if (!first) {
                        break
                    }
                }
            }

            if (shouldSkipNextCause(cause)) break
            cause = cause.cause
        }

        return sw.toString()
    }

    protected Resource getFileNameInfo(Throwable cause, Resource res) {
        Throwable start = cause

        while ((start instanceof SourceCodeAware) || (start instanceof MultipleCompilationErrorsException)) {
            if (start instanceof SourceCodeAware) {
                try {
                    if (start.fileName) {
                        final tmp = new FileSystemResource(start.fileName)
                        if (tmp.exists()) {
                            res = tmp
                            break
                        }
                    }
                } catch (e) {
                    // ignore
                }
            } else if (start instanceof MultipleCompilationErrorsException) {
                MultipleCompilationErrorsException mcee = start
                Object message = mcee.getErrorCollector().getErrors().iterator().next()
                if (message instanceof SyntaxErrorMessage) {
                    SyntaxErrorMessage sem = (SyntaxErrorMessage) message
                    final tmp = new FileSystemResource(sem.getCause().getSourceLocator())
                    if (tmp.exists()) {
                        res = tmp
                        break
                    }
                }
            }

            start = start.cause
            if (start == null || start == start.cause) break
        }
        return res
    }

    protected int getLineNumberInfo(Throwable cause, int defaultInfo) {
        int lineNumber = defaultInfo
        if (cause instanceof SourceCodeAware) {
            SourceCodeAware sca = cause
            lineNumber = sca.lineNumber
        } else if (cause instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException mcee = cause
            Object message = mcee.getErrorCollector().getErrors().iterator().next()
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage sem = (SyntaxErrorMessage) message
                lineNumber = sem.getCause().getLine()
            }
        }
        return lineNumber
    }

    String formatCodeSnippetEnd(Resource resource, int lineNumber) {
        ""
    }

    @Deprecated
    String formatCodeSnippetStart(Resource resource, int lineNumber) {
        formatCodeSnippetStart(resource, lineNumber, [:])
    }

    String formatCodeSnippetStart(Resource resource, int lineNumber, Map attrs) {
        """Around line $lineNumber of $resource.filename
"""
    }

    @Deprecated
    protected String formatCodeSnippetLine(int currentLineNumber, currentLine) {
        formatCodeSnippetLine(currentLineNumber, currentLine, [:])
    }

    protected String formatCodeSnippetLine(int currentLineNumber, currentLine, Map attrs) {
        """${currentLineNumber}: ${currentLine}
"""
    }

    @Deprecated
    protected String formatCodeSnippetErrorLine(int currentLineNumber, currentLine) {
        formatCodeSnippetErrorLine(currentLineNumber, currentLine, [:])
    }

    protected String formatCodeSnippetErrorLine(int currentLineNumber, currentLine, Map attrs) {
        """${currentLineNumber}: ${currentLine}
"""
    }

    /**
     * Obtains the root cause of the given exception
     * @param ex The exception
     * @return The root cause
     */
    protected Throwable getRootCause(Throwable ex) {
        while (ex.getCause() != null && !ex.is(ex.getCause())) {
            ex = ex.getCause()
        }
        return ex
    }
}
