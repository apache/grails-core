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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic

/**
 * Thrown when the jar of a feature pack could not be taken from the repositories. The message is one
 * line that names the coordinates and, for each repository that was asked, why it did not give the jar.
 *
 * @since 9.0
 */
@CompileStatic
class PackArtifactException extends RuntimeException {

    PackArtifactException(String message) {
        super(message)
    }
}
