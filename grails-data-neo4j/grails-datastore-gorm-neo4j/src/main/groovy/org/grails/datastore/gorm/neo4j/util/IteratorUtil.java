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
package org.grails.datastore.gorm.neo4j.util;

import java.util.Iterator;

/**
 * Utility methods for working with Iterable objects
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class IteratorUtil {


    /**
     * A single item or null
     *
     * @param iterable The iterable
     * @param <T> The item
     * @return A single item or null
     */
    public static <T> T singleOrNull(Iterable<T> iterable) {
        Iterator<T> i = iterable.iterator();
        if(i.hasNext()) {
            return i.next();
        }
        return null;
    }

    /**
     * A single item or null
     *
     * @param iterable The iterable
     * @param <T> The item
     * @return A single item or null
     */
    public static <T> T single(Iterable<T> iterable) {
        Iterator<T> i = iterable.iterator();
        return single(i);
    }

    public static <T> T single(Iterator<T> i) {
        if(i.hasNext()) {
            return i.next();
        }
        return null;
    }

    /**
     * @return The count from the iterable
     */
    public static int count(Iterable iterable) {
        Iterator i = iterable.iterator();
        return count(i);
    }

    public static int count(Iterator i) {
        int count = 0;
        while(i.hasNext()) {
            count++;
            i.next();
        }
        return count;
    }
}
