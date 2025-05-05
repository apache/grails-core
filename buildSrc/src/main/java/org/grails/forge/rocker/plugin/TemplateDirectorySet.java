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

import org.gradle.api.PathValidation;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternFilterable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * A simplified version of {@link SourceSet}. Gradle's {@link SourceSet}
 * inherits from {@link PatternFilterable}, but rocker works on complete
 * directories. So instead of using {@link SourceSet} and allowing
 * the user to specify filters without any effect, this simplified
 * source set implementation is used.
 * <P>
 * It would be tempting to provide e.g. {@code templateDir} instead of
 * {@code srcDir}, but this would break established use and templates are
 * the source in this context.
 */
public class TemplateDirectorySet  {
    private Project project;
    private final Set<File> templateDirs = new HashSet<>();

    /**
     * Create a new instance. The project is used to immediately check
     * (with {@code project.file()} the validity of added directories.
     *
     * @param project the project
     */
    public TemplateDirectorySet(Project project) {
        super();
        this.project = project;
    }

    /**
     * Adds the given source directory to this set.
     *
     * @param srcDir the directory
     * @return the template set for easy chaining
     */
    public TemplateDirectorySet srcDir(Object srcDir) {
        templateDirs.add(project.file(srcDir, PathValidation.DIRECTORY));
        return this;
    }

    /**
     * Adds the given source directories to this set.
     *
     * @param srcDirs the directories
     * @return the template set for easy chaining
     */
    public TemplateDirectorySet srcDirs(Object... srcDirs) {
        for (Object srcDir : srcDirs) {
            srcDir(srcDir);
        }
        return this;
    }

    /**
     * Sets the source directories for this set.
     *
     * @param srcPaths the source directories
     * @return the template set for easy chaining
     */
    public TemplateDirectorySet setSrcDirs(Iterable<?> srcPaths) {
        templateDirs.clear();
        srcPaths.forEach(this::srcDir);
        return this;
    }

    /**
     * Adds the given source to this set.
     *
     * @param source the source
     * @return the template set for easy chaining
     */
    public TemplateDirectorySet source(TemplateDirectorySet source) {
        source.getSrcDirs().forEach(this::srcDir);
        return this;
    }

    /**
     * Returns the source directories that make up this set.
     *
     * @return the directories
     */
    public Set<File> getSrcDirs() {
        return templateDirs;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TemplateDirectorySet [");
        if (!templateDirs.isEmpty()) {
            builder.append("templateDirs=");
            builder.append(templateDirs);
        }
        builder.append("]");
        return builder.toString();
    }
}
