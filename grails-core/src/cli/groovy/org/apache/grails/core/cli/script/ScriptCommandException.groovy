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
package org.apache.grails.core.cli.script

import groovy.transform.CompileStatic

/**
 * Says why a script command was not run, or what it used that a script does not have where the
 * build runs it. The message is all there is to say: it is shown without a stack trace.
 *
 * @since 9.0
 */
@CompileStatic
class ScriptCommandException extends RuntimeException {

    ScriptCommandException(String message) {
        super(message)
    }
}
