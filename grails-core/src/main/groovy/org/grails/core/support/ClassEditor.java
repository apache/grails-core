/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.core.support;

import java.beans.PropertyEditorSupport;

import org.springframework.util.ClassUtils;

/**
 * Converts Strings to Class references for Spring.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 *
 * @since Aug 8, 2005
 */
public class ClassEditor extends PropertyEditorSupport {

    private ClassLoader classLoader;

    public ClassEditor() {
        super();
    }

    public ClassEditor(Object source) {
        super(source);
    }

    public void setClassLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            this.classLoader = classLoader;
        }
    }

    @Override
    public String getAsText() {
        return ((Class<?>)getValue()).getName();
    }

    @Override
    public void setAsText(String className) throws IllegalArgumentException {
        try {
            Class<?> clazz = ClassUtils.resolvePrimitiveClassName(className);
            if (clazz != null) {
                setValue(clazz);
            }
            else {
                final ClassLoader cl = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
                setValue(cl.loadClass(className));
            }
        }
        catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not load class [" + className + "]!");
        }
    }
}
