/* Copyright 2012-2013 SpringSource.
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
package grails.plugin.cache;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import org.springframework.cache.support.SimpleValueWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Jakob Drangmeister
 */
public class GrailsConcurrentLinkedMapCache implements GrailsCache {

   private static final Object NULL_HOLDER = new NullHolder();
   private String name;
   private long capacity;
   private final ConcurrentLinkedHashMap<Object, Object> store;
   private final boolean allowNullValues;

   /**
    * Create a new GrailsConcurrentLinkedMapCache with the specified name 
    * and capacity
    * @param name the name of the cache
    * @param capacity of the map
    * @param allowNullValues null values (default is true)
    */
   public GrailsConcurrentLinkedMapCache(String name, long capacity, boolean allowNullValues) {
      this.name = name;
      this.capacity = capacity;
      this.allowNullValues = allowNullValues;
      // Workaround: using explicit type arguments to prevent groovydoc error (#53)
      // Replace with diamond operator once a fix for GROOVY-8628 is included in groovy dependency
      this.store = new ConcurrentLinkedHashMap.Builder<Object, Object>()
         .maximumWeightedCapacity(capacity)
         .build();
   }

   /**
    * Create a new GrailsConcurrentLinkedMapCache with the specified name 
    * and capacity
    * @param name the name of the cache
    * @param capacity of the map
    */
   public GrailsConcurrentLinkedMapCache(String name, long capacity) {
      this(name, capacity, true);
   }

   public final long getCapacity() {
      return this.store.capacity();
   }

   @Override
   public final String getName() {
      return this.name;
   }

   @Override
   public final ConcurrentMap<Object, Object> getNativeCache() {
      return this.store;
   }

   public final int getSize() {
      return this.store.size();
   }

   public final boolean isAllowNullValues() {
      return this.allowNullValues;
   }

   @Override
   public GrailsValueWrapper get(Object key) {
      Object value = getNativeCache().get(key);
      return value == null ? null : new GrailsValueWrapper(fromStoreValue(value), null);
   }

   @Override
  public <T> T get(Object key, Class<T> type) {
    Object value = getNativeCache().get(key);
    if (value != null && type != null && !type.isInstance(value)) {
      throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
    }
    return (T) value;
  }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
   public Collection<Object> getAllKeys() {
      return getNativeCache().keySet();
   }

   public Collection<Object> getHottestKeys() {
      return this.store.descendingKeySet();
   }

   @Override
   public void put(Object key, Object value) {
      this.store.put(key, toStoreValue(value));
   }

   public ValueWrapper putIfAbsent(Object key, Object value) {
      Object existing = this.store.putIfAbsent(key, value);
      return toWrapper(existing);
   }

   @Override
   public void evict(Object key) {
      this.store.remove(key);
   }

   @Override
   public void clear() {
      this.store.clear();
   }

   /**
    * Convert the given value from the internal store to a user value
    * returned from the get method (adapting {@code null}).
    * @param storeValue the store value
    * @return the value to return to the user
    */
   protected Object fromStoreValue(Object storeValue) {
      if (this.allowNullValues && storeValue == NULL_HOLDER) {
         return null;
      }
      return storeValue;
   }

   /**
    * Convert the given user value, as passed into the put method,
    * to a value in the internal store (adapting {@code null}).
    * @param userValue the given user value
    * @return the value to store
    */
   protected Object toStoreValue(Object userValue) {
      if (this.allowNullValues && userValue == null) {
         return NULL_HOLDER;
      }
      return userValue;
   }

   private ValueWrapper toWrapper(Object value) {
      return (value != null ? new SimpleValueWrapper(fromStoreValue(value)) : null);
   }

   @SuppressWarnings("serial")
   
   private static class NullHolder implements Serializable {
   }
}
