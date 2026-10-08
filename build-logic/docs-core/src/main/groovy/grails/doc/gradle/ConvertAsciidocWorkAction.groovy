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
package grails.doc.gradle

import groovy.transform.CompileStatic

import org.gradle.workers.WorkAction

import grails.doc.asciidoc.AsciidocConverter

/**
 * Converts the documents of a {@link ConvertAsciidocTask} in the worker process it forks.
 *
 * @since 8.0
 */
@CompileStatic
abstract class ConvertAsciidocWorkAction implements WorkAction<ConvertAsciidocWorkParameters> {

    @Override
    void execute() {
        ConvertAsciidocWorkParameters params = parameters
        AsciidocConverter.convert(
                params.sourceDir.get().asFile,
                params.outputDir.get().asFile,
                params.documents.get(),
                params.attributes.get()
        )
    }
}
