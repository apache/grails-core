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
package org.grails.forge.api;

import io.micronaut.core.annotation.Introspected;
import org.grails.forge.util.VersionInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Information about the application.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Introspected
@Schema(name = "Version")
public class VersionDTO extends Linkable {

    /**
     * @return The version
     */
    public Map<String, String> getVersions() {
        return VersionInfo.getDependencyVersions();
    }

    @Override
    public VersionDTO addLink(CharSequence rel, LinkDTO link) {
        super.addLink(rel, link);
        return this;
    }
}
