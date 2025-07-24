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

package org.grails.scaffolding.registry

import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic

import org.grails.scaffolding.model.property.DomainProperty

/**
 * A registry of domain property renderers sorted by priority and order of addition
 *
 * @author James Kleeh
 */
@CompileStatic
abstract class DomainRendererRegistry<T extends DomainRenderer> {

    protected SortedSet<Entry> domainRegistryEntries = new TreeSet<Entry>();

    protected final AtomicInteger RENDERER_SEQUENCE = new AtomicInteger(0);

    void registerDomainRenderer(T domainRenderer, Integer priority) {
        domainRegistryEntries.add(new Entry(domainRenderer, priority))
    }

    public SortedSet<Entry> getDomainRegistryEntries() {
        this.domainRegistryEntries
    }

    T get(DomainProperty domainProperty) {
        for (Entry entry : domainRegistryEntries) {
            if (entry.renderer.supports(domainProperty)) {
                return entry.renderer
            }
        }
        null
    }

    private class Entry implements Comparable<Entry> {
        protected final T renderer
        private final int priority;
        private final int seq;

        Entry(T renderer, int priority) {
            this.renderer = renderer
            this.priority = priority
            seq = RENDERER_SEQUENCE.incrementAndGet()
        }

        public int compareTo(Entry entry) {
            return priority == entry.priority ? entry.seq - seq : entry.priority - priority;
        }
    }
}
