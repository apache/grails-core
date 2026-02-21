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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.MappingException;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;

/** Utility class for resolving Grails properties from PersistentClass. */
public class GrailsPropertyResolver {

  /**
   * Retrieves a property from a PersistentClass, with a fallback for composite primary keys.
   *
   * @param associatedClass The PersistentClass to get the property from.
   * @param propertyName The name of the property to retrieve.
   * @return The resolved Property.
   * @throws MappingException if the property cannot be found.
   */
  public Property getProperty(PersistentClass associatedClass, String propertyName)
      throws MappingException {
    try {
      return associatedClass.getProperty(propertyName);
    } catch (MappingException e) {
      // maybe it's squirreled away in a composite primary key
      if (associatedClass.getKey() instanceof Component) {
        return ((Component) associatedClass.getKey()).getProperty(propertyName);
      }
      throw e;
    }
  }
}
