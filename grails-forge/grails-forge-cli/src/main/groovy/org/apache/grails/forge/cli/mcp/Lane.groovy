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
package org.apache.grails.forge.cli.mcp

import groovy.transform.CompileStatic

/**
 * Which calls of {@code grails mcp} run beside which: the calls of one lane wait for each other as
 * the lane says, and the three lanes run beside each other.
 *
 * @since 9.0
 */
@CompileStatic
enum Lane {

    /** Everything that runs Gradle or a tool: one call at a time, in the order they came. */
    BUILD,

    /** The commands that start the project generator: one call at a time. */
    GENERATOR,

    /** Everything else, as the reads of the help, of the project and of the plugins: a few calls at once. */
    DIRECT

}
