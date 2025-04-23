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

package org.grails.gorm.graphql.entity.dsl

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.operations.ListOperation
import org.grails.gorm.graphql.entity.operations.ProvidedOperation

/**
 * Stores metadata about the default operations provided
 * by this library
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class Operations {

    ProvidedOperation mutation = new ProvidedOperation()
    ProvidedOperation query = new ProvidedOperation()
    ProvidedOperation all = new ProvidedOperation()
    ProvidedOperation get = new ProvidedOperation()
    ListOperation list = new ListOperation()
    ProvidedOperation create = new ProvidedOperation()
    ProvidedOperation update = new ProvidedOperation()
    ProvidedOperation delete = new ProvidedOperation()
    ProvidedOperation count = new ProvidedOperation()
}
