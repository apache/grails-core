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
package grails.util

import groovy.transform.CompileStatic

import java.lang.reflect.Array

import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.springframework.util.ObjectUtils

/**
 * Utility methods for working with Arrays
 *
 * @since 2.3.6
 */
@CompileStatic
abstract class GrailsArrayUtils {

    static String toString(Object[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    static String toString(int[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    static String toString(boolean[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    static String toString(float[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    static String toString(short[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    static String toString(byte[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    static String toString(char[] array) {
        ObjectUtils.nullSafeToString(array)
    }

    /**
     * Adds the given object to the end of the array returning a new array
     * @param array The array
     * @param newObject The object
     * @return A new array with the given object added to the end
     */
    static Object addToEnd(Object array, Object newObject) {
        add array, Array.getLength(array), newObject
    }

    /**
     * Adds the given object to the start of the array returning a new array
     * @param array The array
     * @param newObject The object
     * @return A new array with the given object added to the start
     */
    static Object addToStart(Object array, Object newObject) {
        add array, 0, newObject
    }
    /**
     * Adds the given object to the given array at the given position
     *
     * @param array The array
     * @param pos The position
     * @param newObject The object
     * @return A new array, one element bigger, with the object added at the given position
     */
    static Object add(Object array, int pos, Object newObject) {

        if(array == null) {
            Object[] newArray = (Object[])Array.newInstance(newObject.getClass(), 1)
            newArray[pos] = newObject
            return newArray
        }
        else {
            def type = array.getClass().componentType
            int len = Array.getLength(array)
            def newArray = Array.newInstance(type, len + 1)
            System.arraycopy array, 0, newArray, 0, pos
            Array.set newArray, pos, newObject
            if( pos < len ) {
                System.arraycopy array, pos, newArray, pos + 1, len - pos
            }
            return newArray
        }
    }

    /**
     * Adds the given object to the given array at the given position
     *
     * @param array The array
     * @param pos The position
     * @param newObject The object
     * @return A new array, one element bigger, with the object added at the given position
     */
    static Object addAll(Object array, Object otherArray) {
        if(array == null) {
            return otherArray
        }
        else {
            def type = array.getClass().componentType
            int len = Array.getLength( array )
            int len2 = Array.getLength( otherArray )

            def newArray = Array.newInstance(type, len + len2)
            System.arraycopy(array, 0, newArray, 0, len);
            try {
                System.arraycopy otherArray, 0, newArray, len, len2
            } catch (ArrayStoreException ase) {
                throw new IllegalArgumentException("Component types of passed arrays do not match [${array.getClass().componentType}] and [${otherArray.getClass().componentType}]", ase)
            }
            return newArray
        }
    }

    /**
     * Returns the subarray of an existing array
     *
     * @param args The array object
     * @param start The start index (inclusive)
     * @param end The end index (exclusive)
     * @return The new array
     */
    static Object subarray(Object args, int start, int end) {
        def len = Array.getLength(args)

        if(start < 0) start = 0
        if(end > len) end = len

        def type = args.getClass().componentType

        def newLen = end - start
        if(newLen <= 0) {
            return Array.newInstance(type, 0)
        }
        else {
            def newArray = Array.newInstance(type, newLen )
            System.arraycopy args, start, newArray,0, newLen
            return newArray
        }
    }

    static boolean contains(Object[] array, Object elementToSearchFor) {
        boolean found = false
        if(array) {
            found = DefaultGroovyMethods.contains(array, elementToSearchFor)
        }
        found
    }

    static <T> T[] concat(T[] first, T[] second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }

        T[] result = (T[]) java.lang.reflect.Array.newInstance(first.getClass().getComponentType(), first.length + second.length);
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
