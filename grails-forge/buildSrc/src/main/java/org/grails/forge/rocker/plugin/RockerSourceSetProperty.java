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

package org.grails.forge.rocker.plugin;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.ConfigureUtil;

/**
 * The rocker property added to the {@link SourceSet}.
 */
public class RockerSourceSetProperty {

    private final TemplateDirectorySet templateDirs;

    /**
     * @param project - main gradle project
     */
    public RockerSourceSetProperty(Project project) {
        super();
        templateDirs = new TemplateDirectorySet(project);
    }

    public TemplateDirectorySet getRocker() {
        return templateDirs;
    }

    public RockerSourceSetProperty rocker(Closure<?> configureClosure) {
        ConfigureUtil.configure(configureClosure, getRocker());
        return this;
    }

    public RockerSourceSetProperty rocker(
        Action<? super TemplateDirectorySet> configureAction) {
        configureAction.execute(getRocker());
        return this;
    }
}
