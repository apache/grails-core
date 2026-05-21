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

/**
 * Interface for scripts that are writable
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface WritableScript extends Writable, WriterProvider {

    /**
     * Obtains the source file
     */
    File getSourceFile()
    /**
     * @param file Sets the source file
     */
    void setSourceFile(File file)

    /**
     * Sets the binding
     *
     * @param binding The binding
     */
    void setBinding(Binding binding)

    /**
     * @return Obtains the binding
     */
    Binding getBinding()

    /**
     * Runs the script and returns the result
     *
     * @return The result
     */
    Object run()
}
