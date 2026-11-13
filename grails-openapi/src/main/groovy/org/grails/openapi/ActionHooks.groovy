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
package org.grails.openapi

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.Operation

import grails.core.GrailsControllerClass

/**
 * What a document decides by the action itself, and does to each operation it describes, where its
 * selection implements it: springdoc's method filters and operation customizers, applied to the
 * Grails actions. Internal to Grails; a selection is otherwise a plain set of criteria.
 */
@CompileStatic
interface ActionHooks {

    /**
     * Whether the action serving an operation is described, decided by the method it is declared
     * as.
     *
     * @param action the method the action is declared as, or {@code null} where it is not known
     */
    boolean selectsAction(Method action)

    /**
     * Customizes an operation the document describes, once the annotations of its action are
     * applied.
     *
     * @param controller the controller serving the action, where the application has it
     * @param action the method the action is declared as, if known
     * @return the operation to describe, or {@code null} to leave it out
     */
    Operation customize(Operation operation, Components components, GrailsControllerClass controller, Method action)
}
