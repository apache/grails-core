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
 * One call of a tool, as the protocol has it: what the client asked to be told of its progress by, a
 * way to tell it, and whether the client has cancelled the call.
 *
 * @since 9.0
 */
@CompileStatic
interface McpCallContext {

    /**
     * @return the progress token the client gave with the call, or {@code null} when it asked for no progress
     */
    Object getProgressToken()

    /**
     * Tells the client how the call goes, when it asked for progress; does nothing otherwise.
     *
     * @param message one line
     */
    void progress(String message)

    /**
     * @return whether the client has cancelled the call
     */
    boolean isCancelled()

}
