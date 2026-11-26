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
package org.apache.grails.core.plugins.filters;

import java.util.List;

import org.apache.grails.core.plugins.PluginMetadata;

/**
 * A {@link PluginFilter} implementation that performs no filtering.
 *
 * <p>This filter returns the original plugin metadata list unchanged and is useful when no include or
 * exclude rules are configured.</p>
 */
public class NoOpPluginFilter implements PluginFilter {

    /**
     * Returns the original plugin metadata list unchanged.
     *
     * @param original the original plugin metadata list
     * @return the same {@code original} list reference, without modification
     */
    @Override
    public List<PluginMetadata> filterPluginList(List<PluginMetadata> original) {
        return original;
    }
}
