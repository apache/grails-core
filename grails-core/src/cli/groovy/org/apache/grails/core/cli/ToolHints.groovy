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

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Says what a tool does to the project and to the world around it, for a program that decides how
 * carefully to run it: a tool that changes nothing can be run without asking, one that replaces or
 * deletes what the user wrote is asked about first. The members and their defaults are those of the
 * annotations of a tool of the Model Context Protocol, and of {@code @McpTool.McpAnnotations} of
 * Spring AI. The defaults are the most cautious ones, so a tool that says nothing is taken to change
 * and delete things, to do something else each time it runs, and to reach beyond the project.
 *
 * <pre>
 * &#64;ToolHints(readOnlyHint = true)
 * class AuditReportTool implements ApplicationTool { ... }
 * </pre>
 *
 * <p>The members have to be constants: the compiler reads them from the source, without running the
 * tool. They are hints, which a program may trust or not; they are no permission.</p>
 *
 * @since 9.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@interface ToolHints {

    /**
     * @return whether the tool changes nothing: no data, and no file. A tool that writes what it prints to
     * a file whose name the caller gives it is read-only only when it writes that file new, inside the
     * project, and refuses one that is there or outside it. A tool that creates the empty bookkeeping
     * tables a library keeps in a database it is used with for the first time, as {@code dbm-status}
     * creates those of Liquibase, is still read-only. Then the other hints say nothing
     */
    boolean readOnlyHint() default false

    /**
     * @return whether the tool may replace or delete what the user wrote, files or data, where it
     * changes something; {@code false} for a tool that only adds
     */
    boolean destructiveHint() default true

    /**
     * @return whether running the tool again with the same arguments does nothing more than the first
     * run did, where it changes something
     */
    boolean idempotentHint() default false

    /**
     * @return whether the tool reaches beyond the project: a network, a database, a service
     */
    boolean openWorldHint() default true

}
