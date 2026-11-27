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
 * The tools {@code grails mcp} offers in its directory, and what they were made from: the document of
 * {@code help --json} of the directory, whether the directory is in a project, and the fingerprint of
 * the files the tools depend on. Nothing of it can be changed: a change of the directory makes a new one.
 *
 * @since 9.0
 */
@CompileStatic
final class McpToolSet {

    /** The tools, in the order they are listed. */
    final List<McpTool> tools

    /** The document of {@code help --json} the tools are made from. */
    final Map<String, Object> helpDocument

    /** Whether the directory is in a project. */
    final boolean project

    /** The fingerprint of the files the tools depend on, when the tools were made. */
    final String fingerprint

    /**
     * @param tools the tools, in the order they are listed
     * @param helpDocument the document of {@code help --json} they are made from
     * @param project whether the directory is in a project
     * @param fingerprint the fingerprint of the files the tools depend on
     */
    McpToolSet(List<McpTool> tools, Map<String, Object> helpDocument, boolean project, String fingerprint) {
        this.tools = Collections.unmodifiableList(new ArrayList<McpTool>(Objects.requireNonNull(tools, 'tools')))
        this.helpDocument = McpTool.frozen(Objects.requireNonNull(helpDocument, 'helpDocument'))
        this.project = project
        this.fingerprint = Objects.requireNonNull(fingerprint, 'fingerprint')
    }
}
