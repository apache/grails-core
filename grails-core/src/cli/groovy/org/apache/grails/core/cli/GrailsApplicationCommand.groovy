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

import grails.codegen.model.ModelBuilder
import org.apache.grails.core.cli.io.FileSystemInteraction
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.template.TemplateRenderer
import org.apache.grails.core.cli.template.TemplateRendererImpl

trait GrailsApplicationCommand implements ApplicationCommand, ModelBuilder {

    @Delegate TemplateRenderer templateRenderer
    @Delegate FileSystemInteraction fileSystemInteraction
    ExecutionContext executionContext

    boolean handle(ExecutionContext executionContext) {
        this.executionContext = executionContext
        this.templateRenderer = new TemplateRendererImpl(executionContext.baseDir)
        this.fileSystemInteraction = new FileSystemInteractionImpl(executionContext.baseDir)
        handle()
    }

    List<String> getArgs() {
        executionContext.commandLine.remainingArgs
    }

    abstract boolean handle()
}
