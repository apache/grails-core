/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.gorm;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

import org.grails.datastore.mapping.query.Query;

/**
 * A result list implementation that provides an additional property called 'totalCount' to obtain the total number of
 * records. Useful for pagination.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PagedResultList<E> implements Serializable, List<E> {


    private static final long serialVersionUID = -5820655628956173929L;

    private final Query query;
    protected List<E> resultList;
    protected int totalCount = Integer.MIN_VALUE;

    public PagedResultList(Query query) {
        this.query = query;
        this.resultList = query == null ? Collections.<E>emptyList() : query.list();
    }

    /**
     * @return The total number of records for this query
     */
    public int getTotalCount() {
        initialize();
        return totalCount;
    }

    @Override
    public E get(int i) {
        return resultList.get(i);
    }

    @Override
    public E set(int i, E o) {
        return resultList.set(i, o);
    }

    @Override
    public E remove(int i) {
        return resultList.remove(i);
    }

    @Override
    public int indexOf(Object o) {
        return resultList.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return resultList.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return resultList.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return resultList.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return resultList.subList(fromIndex, toIndex);
    }

    @Override
    public void add(int i, E o) {
        resultList.add(i, o);
    }

    protected void initialize() {
        if (totalCount == Integer.MIN_VALUE) {
            if (query == null) {
                totalCount = 0;
            } else {
                Query newQuery = (Query)query.clone();
                newQuery.projections().count();
                Number result = (Number) newQuery.singleResult();
                totalCount = result == null ? 0 : result.intValue();
            }
        }
    }

    @Override
    public int size() {
        return resultList.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return resultList.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return resultList.iterator();
    }

    @Override
    public Object[] toArray() {
        return resultList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return resultList.toArray(a);
    }

    @Override
    public boolean add(E e) {
        return resultList.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return resultList.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return resultList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return resultList.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return resultList.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return resultList.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return resultList.retainAll(c);
    }

    @Override
    public void clear() {
        resultList.clear();
    }

    @Override
    public boolean equals(Object o) {
        return resultList.equals(o);
    }

    @Override
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
