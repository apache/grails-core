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
 * What is wrong with a request to {@code grails mcp}, with the JSON-RPC error code it is answered with
 * where it is answered with an error: {@link #INVALID_PARAMS} for a tool or a resource there is not, and
 * {@link #INTERNAL_ERROR} for a call that arrives while the server ends. Arguments a tool cannot be given
 * are said with one as well, by {@link McpTool#commandLine}, and are answered with a result that is an
 * error and says what is wrong, since they are a mistake of the call and not of the request.
 *
 * @since 9.0
 */
@CompileStatic
final class McpException extends Exception {

    /** The JSON-RPC error code of a request whose parameters are wrong: a tool or a resource there is not, or arguments a tool cannot be given. */
    public static final int INVALID_PARAMS = -32602

    /** The JSON-RPC error code of a request the server cannot answer, as a call that arrives while it ends. */
    public static final int INTERNAL_ERROR = -32603

    private static final long serialVersionUID = 1L

    /** The JSON-RPC error code. */
    final int code

    /**
     * @param code the JSON-RPC error code
     * @param message what is wrong, one line
     */
    McpException(int code, String message) {
        super(message)
        this.code = code
    }
}
