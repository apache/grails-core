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
package org.grails.orm.hibernate.cfg;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;

/** Holder for the GORM mapping cache. */
public class MappingCacheHolder {

  private static final MappingCacheHolder INSTANCE = new MappingCacheHolder();

  private MappingCacheHolder() {}

  public static MappingCacheHolder getInstance() {
    return INSTANCE;
  }

  private final Map<Class<?>, Mapping> MAPPING_CACHE = new HashMap<>();

  /**
   * Obtains a mapping object for the given domain class nam
   *
   * @param theClass The domain class in question
   * @return A Mapping object or null
   */
  public Mapping getMapping(Class<?> theClass) {
    return theClass == null ? null : MAPPING_CACHE.get(theClass);
  }

  /**
   * Obtains a mapping object for the given domain class nam
   *
   * @param entity The domain class in question
   */
  public void cacheMapping(GrailsHibernatePersistentEntity entity) {
    if (entity != null) {
      MAPPING_CACHE.put(entity.getJavaClass(), entity.getMappedForm());
    }
  }

  public void clear() {
    MAPPING_CACHE.clear();
  }

  public void clear(Class<?> theClass) {
    String className = theClass.getName();
    for (Iterator<Map.Entry<Class<?>, Mapping>> it = MAPPING_CACHE.entrySet().iterator();
        it.hasNext(); ) {
      Map.Entry<Class<?>, Mapping> entry = it.next();
      if (className.equals(entry.getKey().getName())) {
        it.remove();
      }
    }
  }
}
