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
package org.grails.forge.api.create.zip;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.Writable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Header;
import org.grails.forge.api.TestFramework;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.options.BuildTool;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.options.JdkVersion;

import javax.validation.constraints.Pattern;
import java.util.List;

/**
 * Defines the signature for creating an application.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public interface ZipCreateOperation {

    /**
     * Creates an application.
     *
     * @param type        The application type
     * @param name        The name of the application
     * @param features    The features
     * @param build       The build tool
     * @param test        The test framework
     * @param gorm        The GORM
     * @param servlet     The Servlet
     * @param javaVersion The java version
     * @param userAgent   The browser user-agent
     * @return An HTTP response that emits a writable
     */
    HttpResponse<Writable> createApp(
            ApplicationType type,
            @Pattern(regexp = "[\\w\\d-_\\.]+") String name,
            @Nullable List<String> features,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable GormImpl gorm,
            @Nullable ServletImpl servlet,
            @Nullable JdkVersion javaVersion,
            @Nullable @Header(HttpHeaders.USER_AGENT) String userAgent
    );
}
