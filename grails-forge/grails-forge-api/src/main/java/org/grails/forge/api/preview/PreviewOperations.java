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
package org.grails.forge.api.preview;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Parameter;
import org.grails.forge.api.RequestInfo;
import org.grails.forge.api.TestFramework;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.ServletImpl;

import java.io.IOException;
import java.util.List;

public interface PreviewOperations {
    /**
     * Previews an application.
     *
     * @param type        The application type
     * @param name        The name of the application
     * @param features    The features
     * @param build       The build tool
     * @param test        The test framework
     * @param gorm        The GORM
     * @param servlet     The Servlet
     * @param javaVersion The java version
     * @param requestInfo The request info
     * @return An HTTP response that emits a writable
     * @throws IOException if an I/O error occurs
     */
    PreviewDTO previewApp(
            @NonNull ApplicationType type,
            String name,
            @Nullable List<String> features,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @Parameter(hidden = true) RequestInfo requestInfo) throws IOException;
}
