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
package org.grails.forge.feature

import groovy.transform.CompileStatic

@CompileStatic
interface OneOfFeature extends Feature {

    /**
     * @return the type the features of the group share, or {@code null} for a feature that is not a
     * class of the framework. Features are told apart from other groups by {@link #getGroup()}; this type
     * only lets compiled code look a feature of the group up by type.
     */
    Class<?> getFeatureClass()

    /**
     * The identifier of the exclusive group this feature belongs to: at most one feature of a group can
     * be selected. The identifier is stable, so that a feature that is not a class of this module can
     * name the group it joins.
     *
     * @return the identifier of the group. The groups of the framework are named in lower case with
     * hyphens, such as {@code database-driver}.
     * @since 9.0
     */
    String getGroup()

    /**
     * @return whether everything that looks for a feature of this group looks for the group's identifier
     * and not for the type of {@link #getFeatureClass()}. Only then can a feature that is a pack be in
     * the group beside this one: a default that looked for the type would not see the pack and would be
     * selected with it.
     * @since 9.0
     */
    default boolean isFoundByGroup() {
        return false
    }
}
