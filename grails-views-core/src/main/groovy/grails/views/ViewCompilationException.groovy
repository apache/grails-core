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
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage

import org.grails.exceptions.reporting.SourceCodeAware

/**
 * Exception when views fail to compile
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ViewCompilationException extends ViewException implements SourceCodeAware {

    final String fileName

    ViewCompilationException(CompilationFailedException cause, String fileName) {
        super(cause)
        this.fileName = fileName
    }

    @Override
    int getLineNumber() {
        def cause = getCause()
        ASTNode node = ((CompilationFailedException) cause).getNode()

        if(node != null) {
            return node.getLineNumber()
        }
        else if(cause instanceof MultipleCompilationErrorsException) {
            MultipleCompilationErrorsException mce = (MultipleCompilationErrorsException)cause
            def message = mce.errorCollector.errors[0]
            if(message instanceof SyntaxErrorMessage) {
                return ((SyntaxErrorMessage)message).getCause().line
            }
        }
        return -1
    }
}
