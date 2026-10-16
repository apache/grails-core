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
package org.grails.datastore.gorm.query

import groovy.transform.CompileStatic

/**
 * An abstract result list for initializing objects lazily from a cursor
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
abstract class AbstractResultList extends AbstractList implements Closeable {

    protected int offset = 0
    protected final List initializedObjects
    private int internalIndex
    private Integer size
    protected boolean initialized = false
    protected Iterator<Object> cursor

    AbstractResultList(int offset, Iterator<Object> cursor) {
        this(offset, -1, cursor)
    }

    AbstractResultList(int offset, Integer size, Iterator<Object> cursor) {
        this.offset = offset
        boolean hasSize = size != null && size > -1
        if (hasSize) {
            this.size = size
        }
        this.cursor = cursor
        this.initialized = !cursor.hasNext()
        this.initializedObjects = hasSize ? new ArrayList(size) : new ArrayList()
    }

    Iterator<Object> getCursor() {
        return cursor
    }

    protected void initializeFully() {
        if (initialized) {
            return
        }

        while (cursor.hasNext()) {
            convertObject()
        }
        initialized = true
    }

    @Override
    boolean isEmpty() {
        if (initialized) {
            return initializedObjects.isEmpty()
        }
        else {
            return initializedObjects.isEmpty() && !cursor.hasNext()
        }
    }

    @SuppressWarnings('unchecked')
    @Override
    Object get(int index) {
        final List initializedObjects = this.initializedObjects
        final int initializedSize = initializedObjects.size()
        if (initializedSize > index) {
            return initializedObjects.get(index)
        }
        else if (!initialized) {
            while (cursor.hasNext()) {
                Object o = convertObject()
                if (index == internalIndex) {
                    return o
                }
                else if (index < initializedSize) {
                    return initializedObjects.get(index)
                }
            }
            initialized = true
        }
        return initializedObjects.get(index)
    }

    protected Object convertObject() {
        final Object next = convertObject(nextDecoded())
        if (!cursor.hasNext()) {
            initialized = true
        }
        initializedObjects.add(next)
        internalIndex = initializedObjects.size()
        return next
    }

    protected Object convertObject(Object o) {
        return o
    }

    protected abstract Object nextDecoded()

    @Override
    Object set(int index, Object o) {
        Object previous = get(index)
        initializedObjects.set(index, o)
        return previous
    }

    @Override
    void add(int index, Object element) {
        initializeFully()
        this.initializedObjects.add(index, element)
    }

    @Override
    Object remove(int index) {
        initializeFully()
        return this.initializedObjects.remove(index)
    }

    @Override
    ListIterator listIterator() {
        return listIterator(0)
    }

    @Override
    ListIterator listIterator(int index) {
        initializeFully()
        return initializedObjects.listIterator(index)
    }

    /**
     * Override to transform elements if necessary during iteration.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    @Override
    Iterator iterator() {
        if (initialized || !initializedObjects.isEmpty()) {
            if (!initialized) {
                initializeFully()
            }
            return initializedObjects.iterator()
        }

        return new Iterator() {
            int iteratorIndex = 0
            Object current

            boolean hasNext() {
                if (iteratorIndex < internalIndex) {
                    return true
                }
                else if (!initialized) {
                    boolean hasMore = cursor.hasNext()
                    if (!hasMore) {
                        initialized = true
                    }
                    return hasMore
                }
                return false
            }

            @SuppressWarnings('unchecked')
            Object next() {
                if (iteratorIndex < internalIndex) {
                    current = initializedObjects.get(iteratorIndex)
                }
                else {
                    current = convertObject()
                }
                try {
                    return current
                }
                finally {
                    iteratorIndex++
                }
            }

            void remove() {
                if (current != null) {
                    initializedObjects.remove(current)
                }
            }
        }
    }

    @Override
    int size() {
        if (initialized) {
            return initializedObjects.size()
        }
        else if (this.size == null) {
            initializeFully()
            this.size = initializedObjects.size()
        }
        return size
    }
}
