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

import groovy.transform.CompileStatic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Describes one part of a document at a time, so an application does not lose its whole
 * description because one class cannot be introspected.
 */
@CompileStatic
class DocumentParts {

    private static final Logger LOG = LoggerFactory.getLogger('grails.openapi.GrailsOpenApiGenerator')

    /**
     * Runs the work that describes a part, leaving the rest of the document intact, and logging
     * the part, if it fails.
     *
     * @param what the part, as the log names it
     */
    static void describe(String what, Closure<?> work) {
        try {
            work.call()
        }
        catch (Exception | LinkageError e) {
            LOG.warn('Skipping {} in the OpenAPI document: {}', what, e.message)
            LOG.debug('Could not describe {}', what, e)
        }
    }
}
