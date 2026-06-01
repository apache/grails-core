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
package org.grails.forge.template;

import org.grails.forge.application.Project;
import org.grails.forge.io.OutputHandler;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;

public interface TemplateRenderer extends Closeable {

    default RenderResult render(Template template) throws IOException {
        return render(template, false);
    }

    RenderResult render(Template template, boolean force) throws IOException;

    static TemplateRenderer create(OutputHandler outputHandler) {
        return new DefaultTemplateRenderer(Collections.emptyMap(), outputHandler);
    }

    static TemplateRenderer create(Project project, OutputHandler outputHandler) {
        return new DefaultTemplateRenderer(project.getProperties(), outputHandler);
    }
}
