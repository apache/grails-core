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

package org.grails.exceptions

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage

/**
 * Utility methods for dealing with exception
 *
 * @since 2.4
 * @author Graeme Rocher
 */
@CompileStatic
class ExceptionUtils {

    public static final String EXCEPTION_ATTRIBUTE = "exception"


    static RuntimeException getFirstRuntimeException(Throwable e) {
        if (e instanceof RuntimeException) return (RuntimeException) e

        Throwable ex = e
        while (ex.cause && ex != ex.cause) {
            ex = ex.cause
            if (ex instanceof RuntimeException) return (RuntimeException) ex
        }
    }

    /**
     * Obtains the root cause of the given exception
     * @param ex The exception
     * @return The root cause
     */
    static Throwable getRootCause(Throwable ex) {
        while (ex.cause && ex != ex.cause) {
            ex = ex.cause
        }
        return ex
    }

    static int extractLineNumber(CompilationFailedException e) {
        int lineNumber = -1
        if (e instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException mcee = (MultipleCompilationErrorsException)e
            Object message = mcee.errorCollector.errors.iterator().next()
            if (message instanceof SyntaxErrorMessage) {
                SyntaxErrorMessage sem = (SyntaxErrorMessage)message
                lineNumber = sem.cause.line
            }
        }
        return lineNumber
    }
}
