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
package org.apache.grails.core.cli

import groovy.transform.CompileStatic

import org.springframework.context.ConfigurableApplicationContext

/**
 * A tool the build of the project runs: with the classes of the project and what it depends on, and
 * with the application started around it. Its source is in {@code src/cli} of an application or a
 * plugin.
 *
 * <p>A tool that needs the classes of the project and not the running application says so with
 * {@link #getStartsApplication() startsApplication}:</p>
 * <pre>
 * class ImportSchemaTool implements ApplicationTool {
 *
 *     boolean startsApplication = false
 *
 *     boolean handle(ExecutionContext executionContext) { ... }
 * }
 * </pre>
 * <p>The build then compiles the project and runs the tool with the same class path, makes no
 * application context, injects nothing into the tool and reads no {@code skipBootstrap} property of
 * it: {@link #getApplicationContext()} is {@code null}. The value has to be the constant
 * {@code true} or {@code false}: the build reads it from the source, without running the tool.</p>
 *
 * @since 9.0
 */
@CompileStatic
trait ApplicationTool implements GrailsTool {

    /** The context of the running application; {@code null} for a tool that does not start the application. */
    ConfigurableApplicationContext applicationContext

    /** Whether the application is started around the tool when it is run, which it is unless the tool says otherwise. */
    boolean startsApplication = true

}
