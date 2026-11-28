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
package org.apache.grails.security.ui.cli

import groovy.transform.CompileStatic

/**
 * What the commands of the Spring Security UI plugin share.
 */
@CompileStatic
trait CommandLineHelper {

    static final boolean SUCCESS = true
    static final boolean FAILURE = false

    /** The commands only write files: the BootStrap classes of the application are not run for them. */
    boolean skipBootstrap = true

    /**
     * @param name what was given as the name of a package or of a class
     * @return whether it is one: identifiers with a dot between them, so that it is also a path in the project
     */
    boolean isQualifiedName(String name) {
        if (!name) {
            return false
        }
        for (String part in name.split('\\.', -1)) {
            if (!part || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return false
            }
            for (int i = 1; i < part.length(); i++) {
                if (!Character.isJavaIdentifierPart(part.charAt(i))) {
                    return false
                }
            }
        }
        true
    }
}
