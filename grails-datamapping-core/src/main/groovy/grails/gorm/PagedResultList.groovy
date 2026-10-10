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
package grails.gorm

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.query.Query

/**
 * A result list implementation that provides an additional property called 'totalCount' to obtain the total number of
 * records. Useful for pagination.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings(['rawtypes', 'unchecked'])
@CompileStatic
class PagedResultList<E> implements PagedList<E> {

    private static final long serialVersionUID = -5820655628956173929L

    private final Query query
    protected List<E> resultList
    protected int totalCount = Integer.MIN_VALUE

    PagedResultList(Query query) {
        this.query = query
        this.resultList = query == null ? Collections.<E> emptyList() : query.list()
    }

    @Override
    List<E> getResultList() {
        return resultList
    }

    Query getQuery() {
        return query
    }

    @Override
    int getMax() {
        if (query == null) {
            return -1
        }
        Integer max = query.getMax()
        return max != null ? max : -1
    }

    @Override
    int getOffset() {
        if (query == null) {
            return 0
        }
        Integer offset = query.getOffset()
        return offset != null ? offset : 0
    }

    /**
     * @return The total number of records for this query
     */
    int getTotalCount() {
        initialize()
        return totalCount
    }

    E get(int i) {
        return resultList.get(i)
    }

    E set(int i, E o) {
        return resultList.set(i, o)
    }

    E remove(int i) {
        return resultList.remove(i)
    }

    int indexOf(Object o) {
        return resultList.indexOf(o)
    }

    int lastIndexOf(Object o) {
        return resultList.lastIndexOf(o)
    }

    ListIterator<E> listIterator() {
        return resultList.listIterator()
    }

    ListIterator<E> listIterator(int index) {
        return resultList.listIterator(index)
    }

    List<E> subList(int fromIndex, int toIndex) {
        return resultList.subList(fromIndex, toIndex)
    }

    void add(int i, E o) {
        resultList.add(i, o)
    }

    protected void initialize() {
        if (totalCount == Integer.MIN_VALUE) {
            if (query == null) {
                totalCount = 0
            }
            else {
                Query newQuery = (Query) query.clone()
                newQuery.offset(0)
                newQuery.max(-1)
                newQuery.clearOrders()
                newQuery.projections().count()
                Number result = (Number) newQuery.singleResult()
                totalCount = result == null ? 0 : result.intValue()
            }
        }
    }

    int size() {
        return resultList.size()
    }

    boolean isEmpty() {
        return size() == 0
    }

    boolean contains(Object o) {
        return resultList.contains(o)
    }

    Iterator<E> iterator() {
        return resultList.iterator()
    }

    Object[] toArray() {
        return resultList.toArray()
    }

    def <T> T[] toArray(T[] a) {
        return resultList.toArray(a)
    }

    boolean add(E e) {
        return resultList.add(e)
    }

    boolean remove(Object o) {
        return resultList.remove(o)
    }

    boolean containsAll(Collection<?> c) {
        return resultList.containsAll(c)
    }

    boolean addAll(Collection<? extends E> c) {
        return resultList.addAll(c)
    }

    boolean addAll(int index, Collection<? extends E> c) {
        return resultList.addAll(index, c)
    }

    boolean removeAll(Collection<?> c) {
        return resultList.removeAll(c)
    }

    boolean retainAll(Collection<?> c) {
        return resultList.retainAll(c)
    }

    void clear() {
        resultList.clear()
    }

    boolean equals(Object o) {
        return resultList.equals(o)
    }

    int hashCode() {
        return resultList.hashCode()
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        // find the total count if it hasn't been done yet so when this is deserialized
        // the null GrailsHibernateTemplate won't be an issue
        getTotalCount()

        out.defaultWriteObject()
    }

}
