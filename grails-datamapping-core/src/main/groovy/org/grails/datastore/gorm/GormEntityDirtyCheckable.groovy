/*
 * Copyright 2014 the original author or authors.
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
package org.grails.datastore.gorm

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty

import jakarta.annotation.Generated

/**
 *
 * Special trait meant only for GORM entities to override default behaviour of DirtyCheckable.
 * Applied manually during GormEntity transformation
 *
 * @since 7.3
 */
@CompileStatic
trait GormEntityDirtyCheckable extends DirtyCheckable {
    
    @Override
    @Generated
    boolean hasChanged(String propertyName) {
        PersistentEntity entity = currentGormInstanceApi().persistentEntity
        
        PersistentProperty persistentProperty = entity.getPropertyByName(propertyName)
        if (!persistentProperty) {
            // Not persistent property, transient. We don't track changes for transients 
            return false
        }
        
        Property propertyMapping = persistentProperty.getMapping().getMappedForm() 
        if (propertyMapping.derived) {
            // Derived property cannot be changed, ex. sql formula
            return false
        }

        return super.hasChanged(propertyName)
    }

    @Generated
    private GormInstanceApi currentGormInstanceApi() {
        (GormInstanceApi) GormEnhancer.findInstanceApi(getClass())
    }
}
