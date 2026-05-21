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
package org.grails.exceptions.reporting

/**
 * Interfaces for classes to implement that print code snippets
 *
 * @author Graeme Rocher
 * @since 2.2
 */
interface CodeSnippetPrinter {

    /**
     * Attempts to present a preview code snippet of the code that went wrong
     *
     * @param exception The exception
     * @param optional attributes for formatting
     * @return The code snippet or nothing if it can't be previewed
     */
    String prettyPrintCodeSnippet(Throwable exception, Map attrs)

    @Deprecated
    String prettyPrintCodeSnippet(Throwable exception)
}
