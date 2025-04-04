/*
 * Copyright 2015 original authors
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
package org.grails.datastore.gorm.bootstrap.support;

import org.springframework.beans.factory.FactoryBean;

/**
 * Simple singleton instance implementation of Spring's FactoryBean interface
 *
 * mainly useful in unit tests
 *
 */

public class InstanceFactoryBean<T> implements FactoryBean<T> {
    T object;
    Class<?> objectType;

    public InstanceFactoryBean() {

    }

    public InstanceFactoryBean(T object, Class<?> objectType) {
        this.object = object;
        this.objectType = objectType;
    }

    public InstanceFactoryBean(T object) {
        this.object = object;
        this.objectType = object.getClass();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public T getObject() {
        return object;
    }


    public void setObject(T object) {
        this.object = object;
    }

    @Override
    public Class<?> getObjectType() {
        return objectType != null ? objectType : object.getClass();
    }

    public void setObjectType(Class<?> objectType) {
        this.objectType = objectType;
    }
}
