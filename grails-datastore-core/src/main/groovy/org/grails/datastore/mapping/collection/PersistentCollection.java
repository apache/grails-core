/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.collection;

import org.grails.datastore.mapping.dirty.checking.DirtyCheckableCollection;

import java.util.Collection;

/**
 * A lazy loaded collection.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public interface PersistentCollection extends Collection, DirtyCheckableCollection {

    /**
     * Check whether the collection has been loaded.
     * @return <code>true</code> if the collection has been initialized
     */
    boolean isInitialized();

    /**
     * Initializes the collection if it hasn't already been initialized.
     */
    void initialize();

    /**
     * Check whether the collection has been modified.
     * @return <code>true</code> if the collection is initialized and has been changed since initialization
     */
    boolean isDirty();

    /**
     * Mark the collection as no longer dirty.
     */
    void resetDirty();

    /**
     * Mark the collection as dirty.
     */
    void markDirty();
}
