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
package grails.gorm;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * An interface for result lists that are paged and have a totalCount
 *
 * @param <E> The element type
 * @since 1.0
 */
public interface PagedList<E> extends List<E>, Serializable {

    /**
     * @return The total number of records for this query
     */
    int getTotalCount();

    /**
     * @return The underlying result list
     */
    List<E> getResultList();

    @Override
    default int size() {
        return getResultList().size();
    }

    @Override
    default boolean isEmpty() {
        return getResultList().isEmpty();
    }

    @Override
    default boolean contains(Object o) {
        return getResultList().contains(o);
    }

    @Override
    default Iterator<E> iterator() {
        return getResultList().iterator();
    }

    @Override
    default Object[] toArray() {
        return getResultList().toArray();
    }

    @Override
    default <T> T[] toArray(T[] a) {
        return getResultList().toArray(a);
    }

    @Override
    default boolean add(E e) {
        return getResultList().add(e);
    }

    @Override
    default boolean remove(Object o) {
        return getResultList().remove(o);
    }

    @Override
    default boolean containsAll(Collection<?> c) {
        return getResultList().containsAll(c);
    }

    @Override
    default boolean addAll(Collection<? extends E> c) {
        return getResultList().addAll(c);
    }

    @Override
    default boolean addAll(int index, Collection<? extends E> c) {
        return getResultList().addAll(index, c);
    }

    @Override
    default boolean removeAll(Collection<?> c) {
        return getResultList().removeAll(c);
    }

    @Override
    default boolean retainAll(Collection<?> c) {
        return getResultList().retainAll(c);
    }

    @Override
    default void clear() {
        getResultList().clear();
    }

    @Override
    default E get(int index) {
        return getResultList().get(index);
    }

    @Override
    default E set(int index, E element) {
        return getResultList().set(index, element);
    }

    @Override
    default void add(int index, E element) {
        getResultList().add(index, element);
    }

    @Override
    default E remove(int index) {
        return getResultList().remove(index);
    }

    @Override
    default int indexOf(Object o) {
        return getResultList().indexOf(o);
    }

    @Override
    default int lastIndexOf(Object o) {
        return getResultList().lastIndexOf(o);
    }

    @Override
    default ListIterator<E> listIterator() {
        return getResultList().listIterator();
    }

    @Override
    default ListIterator<E> listIterator(int index) {
        return getResultList().listIterator(index);
    }

    @Override
    default List<E> subList(int fromIndex, int toIndex) {
        return getResultList().subList(fromIndex, toIndex);
    }
}
