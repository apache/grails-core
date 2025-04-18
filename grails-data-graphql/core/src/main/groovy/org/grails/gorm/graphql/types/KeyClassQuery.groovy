package org.grails.gorm.graphql.types

import groovy.transform.CompileStatic

/**
 * Generic class to help searching maps that have a class as their key
 *
 * @param <V> The type of value to return
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait KeyClassQuery<V> {

    /**
     * Searches for exact matches first. If no exact match found,
     * query the set of classes in reverse order and search for any class
     * that is a super class of the class being searched. Return the first
     * result found.
     *
     * @param map The map to search
     * @param clazz The class to search for
     * @param reverse Whether to search in reverse order (last in has priority)
     *
     * @return The result. If no result found, returns NULL.
     */
    V searchMap(Map<Class, V> map, Class clazz, boolean reverse = true) {
        if (map.containsKey(clazz)) {
            return map.get(clazz)
        }
        List<Class> keys = map.keySet().toList()
        if (reverse) {
            keys.reverse(true)
        }
        for (Class key: keys) {
            if (key.isAssignableFrom(clazz)) {
                return map.get(key)
            }
        }
        null
    }

    /**
     * Searches for any class that is a super class of the class being
     * searched. Return all results found.
     *
     * @param map The map to search
     * @param clazz The class to search for
     *
     * @return The result. If no results found, returns an empty list.
     */
    List searchMapAll(Map<Class, V> map, Class clazz) {
        List values = []
        List<Class> keys = map.keySet().toList()
        for (Class key: keys) {
            if (key.isAssignableFrom(clazz)) {
                V value = map.get(key)
                if (value instanceof Collection) {
                    values.addAll((Collection)value)
                }
                else {
                    values.add(value)
                }
            }
        }
        values
    }
}
