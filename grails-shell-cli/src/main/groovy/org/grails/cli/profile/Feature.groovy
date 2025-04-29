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
package org.grails.cli.profile

import org.eclipse.aether.graph.Dependency
import org.grails.config.NavigableMap
import org.grails.io.support.Resource


/**
 * An interface that describes a feature of a profile. Different profiles may share many common features even if the profile itself is different.
 *
 * @author Graeme Rocher
 * @since 3.1
 */
interface Feature {

    /**
     * @return The profile this feature belongs to
     */
    Profile getProfile()

    /**
     * @return The name of the feature
     */
    String getName()

    /**
     * @return The description of the profile
     */
    String getDescription()

    /**
     * @return The location of the feature
     */
    Resource getLocation()

    /**
     * @return The dependency definitions for this feature
     */
    List<Dependency> getDependencies()

    /**
     * @return The build plugin names
     */
    List<String> getBuildPlugins()

    /**
     * @return The build repositories url
     */
    List<String> getBuildRepositories()

    /**
     * @return The configuration for the feature
     */
    NavigableMap getConfiguration()
}