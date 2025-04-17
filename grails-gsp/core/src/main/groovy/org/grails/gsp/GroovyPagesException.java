/*
 * Copyright 2004-2005 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp;

import org.grails.core.exceptions.GrailsException;
import org.grails.exceptions.reporting.SourceCodeAware;

/**
 * Thrown when processing GSP pages.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class GroovyPagesException extends GrailsException implements SourceCodeAware {

    private static final long serialVersionUID = 6142857809397583528L;
    private int lineNumber;
    private String fileName;

    public GroovyPagesException(String message, Throwable e) {
        super(message,e);
    }

    public GroovyPagesException(String message, Throwable exception, int lineNumber, String fileName) {
        super(message, exception);
        this.lineNumber = lineNumber;
        this.fileName = fileName;
    }

    public GroovyPagesException(String message) {
        super(message);
    }

    public String getFileName() {
        if (fileName == null && getCause() instanceof SourceCodeAware) {
            return ((SourceCodeAware)getCause()).getFileName();
        }
        return fileName;
    }

    public int getLineNumber() {
        if (lineNumber == -1 && getCause() instanceof SourceCodeAware) {
            return ((SourceCodeAware)getCause()).getLineNumber();
        }
        return lineNumber;
    }
}
