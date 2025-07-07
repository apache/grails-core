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
package org.grails.forge.options;

import org.grails.forge.application.Project;

/**
 * Abstract implementation for {@link JunitRockerModelProvider} which supplies a {@link Project} in the constructor. You can use it to provide rocker models for each language supported by {@link TestFramework#JUNIT}.
 * @author Sergio del Amo
 */
public abstract class AbstractJunitRockerModelProvider implements JunitRockerModelProvider {

    private final Project project;

    public AbstractJunitRockerModelProvider(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return this.project;
    }
}
