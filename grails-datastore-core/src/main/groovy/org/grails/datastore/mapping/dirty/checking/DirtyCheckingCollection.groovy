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

package org.grails.datastore.mapping.dirty.checking

import groovy.transform.CompileStatic

/**
 * Collection capable of marking the parent entity as dirty when it is modified
 *
 * @author Graeme Rocher
 * @since 4.1
 */
@CompileStatic
class DirtyCheckingCollection implements Collection, DirtyCheckableCollection {

    final @Delegate
    Collection target
    final DirtyCheckable parent
    final String property
    final int originalSize

    DirtyCheckingCollection(Collection target, DirtyCheckable parent, String property) {
        this.target = target
        this.originalSize = target.size()
        this.parent = parent
        this.property = property
    }

    @Override
    boolean hasGrown() {
        return size() > originalSize
    }

    @Override
    boolean hasShrunk() {
        return size() < originalSize
    }

    @Override
    boolean hasChangedSize() {
        return size() != originalSize
    }

    boolean hasChanged() {
        parent.hasChanged(property) || hasChangedElements()
    }

    protected boolean hasChangedElements() {
        target.any { (it instanceof DirtyCheckable) && ((DirtyCheckable) it).hasChanged() }
    }

    @Override
    boolean add(Object o) {
        parent.markDirty(property)
        target.add o
    }

    @Override
    boolean addAll(Collection c) {
        parent.markDirty(property)
        target.addAll(c)
    }

    @Override
    boolean removeAll(Collection c) {
        parent.markDirty(property)
        target.removeAll(c)
    }

    @Override
    void clear() {
        parent.markDirty(property)
        target.clear()
    }

    @Override
    boolean remove(Object o) {
        parent.markDirty(property)
        target.remove(o)
    }

}

