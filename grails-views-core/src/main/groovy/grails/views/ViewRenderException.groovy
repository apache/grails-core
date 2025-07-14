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

package grails.views

import groovy.transform.CompileStatic
import org.grails.exceptions.reporting.SourceCodeAware

/**
 * Thrown when a view rendering exception occurs
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ViewRenderException extends ViewException implements SourceCodeAware {

    final File sourceFile
    final WritableScript view
    final int lineNumber

    ViewRenderException(String message, Throwable cause, WritableScript view) {
        super(message, cause)
        this.sourceFile = view.sourceFile
        this.view = view
        this.lineNumber = findFirstElementCausedByScript()?.lineNumber ?: -1
    }

    @Override
    String getFileName() {
        sourceFile.canonicalPath
    }

    StackTraceElement findFirstElementCausedByScript() {
        def cause = getCause()
        while (cause != null) {
            for (StackTraceElement e in cause.stackTrace) {
                def cls = e.className
                if (cls.contains('$')) {
                    cls = cls.substring(0, cls.indexOf('$'))
                }
                if (cls == view.getClass().name) {
                    return e
                }
            }
            cause = cause.getCause()
        }
        return null
    }
}
