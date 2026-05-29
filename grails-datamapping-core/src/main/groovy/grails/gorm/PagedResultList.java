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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.grails.datastore.mapping.query.Query;

/**
 * A result list implementation that provides an additional property called 'totalCount' to obtain the total number of
 * records. Useful for pagination.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PagedResultList<E> implements PagedList<E> {

    private static final long serialVersionUID = -5820655628956173929L;

    private final Query query;
    protected List<E> resultList;
    protected int totalCount = Integer.MIN_VALUE;

    public PagedResultList(Query query) {
        this.query = query;
        this.resultList = query == null ? Collections.<E>emptyList() : query.list();
    }

    @Override
    public List<E> getResultList() {
        return resultList;
    }

    public Query getQuery() {
        return query;
    }

    @Override
    public int getMax() {
        if (query == null) return -1;
        Integer max = query.getMax();
        return max != null ? max : -1;
    }

    @Override
    public int getOffset() {
        if (query == null) return 0;
        Integer offset = query.getOffset();
        return offset != null ? offset : 0;
    }

    /**
     * @return The total number of records for this query
     */
    public int getTotalCount() {
        initialize();
        return totalCount;
    }

    public E get(int i) {
        return resultList.get(i);
    }

    public E set(int i, E o) {
        return resultList.set(i, o);
    }

    public E remove(int i) {
        return resultList.remove(i);
    }

    public int indexOf(Object o) {
        return resultList.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return resultList.lastIndexOf(o);
    }

    public ListIterator<E> listIterator() {
        return resultList.listIterator();
    }

    public ListIterator<E> listIterator(int index) {
        return resultList.listIterator(index);
    }

    public List<E> subList(int fromIndex, int toIndex) {
        return resultList.subList(fromIndex, toIndex);
    }

    public void add(int i, E o) {
        resultList.add(i, o);
    }

    protected void initialize() {
        if (totalCount == Integer.MIN_VALUE) {
            if (query == null) {
                totalCount = 0;
            } else {
                Query newQuery = (Query) query.clone();
                newQuery.offset(0);
                newQuery.max(-1);
                newQuery.clearOrders();
                newQuery.projections().count();
                Number result = (Number) newQuery.singleResult();
                totalCount = result == null ? 0 : result.intValue();
            }
        }
    }

    public int size() {
        return resultList.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean contains(Object o) {
        return resultList.contains(o);
    }

    public Iterator<E> iterator() {
        return resultList.iterator();
    }

    public Object[] toArray() {
        return resultList.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return resultList.toArray(a);
    }

    public boolean add(E e) {
        return resultList.add(e);
    }

    public boolean remove(Object o) {
        return resultList.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return resultList.containsAll(c);
    }

    public boolean addAll(Collection<? extends E> c) {
        return resultList.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends E> c) {
        return resultList.addAll(index, c);
    }

    public boolean removeAll(Collection<?> c) {
        return resultList.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return resultList.retainAll(c);
    }

    public void clear() {
        resultList.clear();
    }

    public boolean equals(Object o) {
        return resultList.equals(o);
    }

    public int hashCode() {
        return resultList.hashCode();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {

        // find the total count if it hasn't been done yet so when this is deserialized
        // the null GrailsHibernateTemplate won't be an issue
        getTotalCount();

        out.defaultWriteObject();
    }

}
