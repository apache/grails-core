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

package org.grails.forge.template

import groovy.transform.CompileStatic

/**
 * A file of the generated project written by rendering a page of the generator.
 */
@CompileStatic
class GspTemplate implements Template {

    private final String path
    private final GspView view
    private final boolean executable

    GspTemplate(String path, GspView view) {
        this(path, view, false)
    }

    GspTemplate(String path, GspView view, boolean executable) {
        this.path = path
        this.view = view
        this.executable = executable
    }

    @Override
    void write(OutputStream outputStream) throws IOException {
        view.write(outputStream)
    }

    @Override
    String getPath() {
        return path
    }

    @Override
    boolean isExecutable() {
        return executable
    }

    GspView getView() {
        return view
    }
}
