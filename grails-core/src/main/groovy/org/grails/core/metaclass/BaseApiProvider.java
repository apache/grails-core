/*
 * Copyright 2010 the original author or authors.
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
package org.grails.core.metaclass;

import grails.util.GrailsNameUtils;
import groovy.lang.GString;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import grails.util.GrailsArrayUtils;
import grails.util.GrailsClassUtils;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.runtime.metaclass.ReflectionMetaMethod;

/**
 * @author Graeme Rocher
 * @since 2.0
 * @deprecated Use traits instead
 */
@Deprecated
public abstract class BaseApiProvider {

    private static List<String> EXCLUDED_METHODS = Arrays.asList("setMetaClass", "getMetaClass", "setProperties", "getProperties");

    public static final String CONSTRUCTOR_METHOD = "initialize";
    public static final String CTOR_GROOVY_METHOD = "<ctor>";

    @SuppressWarnings("rawtypes")
    protected List instanceMethods = new ArrayList();
    protected List<Method> staticMethods = new ArrayList<Method>();
    protected List<Method> constructors = new ArrayList<Method>();

    @SuppressWarnings("unchecked")
    public void addApi(final Object apiInstance) {
        if (apiInstance == null) {
            return;
        }

        Class<?> currentClass = apiInstance.getClass();
        while (currentClass != Object.class) {
            final Method[] declaredMethods = currentClass.getDeclaredMethods();

            for (final Method javaMethod : declaredMethods) {
                final int modifiers = javaMethod.getModifiers();
                if (!isNotExcluded(javaMethod, modifiers)) {
                    continue;
                }

                if (Modifier.isStatic(modifiers)) {
                    if (isConstructorCallMethod(javaMethod)) {
                        constructors.add(javaMethod);
                    }
                    else {
                        staticMethods.add(javaMethod);
                    }
                }
                else {
                    instanceMethods.add(new ReflectionMetaMethod(new CachedMethod(javaMethod)) {
                        {
                            CachedClass[] paramTypes = super.getParameterTypes();
                            if(paramTypes.length > 0) {
                                setParametersTypes((CachedClass[]) GrailsArrayUtils.subarray(paramTypes, 1, paramTypes.length));
                            }
                        }
                        
                        @Override
                        public String getName() {
                            String methodName = super.getName();
                            if (isConstructorCallMethod(javaMethod)) {
                                return CTOR_GROOVY_METHOD;
                            }
                            return methodName;
                        }
                        
                        @Override
                        public Object invoke(Object object, Object[] arguments) {
                            if (arguments.length == 0) {
                                return super.invoke(apiInstance, new Object[]{object});
                            }
                            return super.invoke(apiInstance, (Object[])GrailsArrayUtils.add(checkForGStrings(arguments), 0, object));
                        }

                        private Object[] checkForGStrings(Object[] arguments) {
                            for (int i = 0; i < arguments.length; i++) {
                                if (arguments[i] instanceof GString) {
                                    arguments[i] = arguments[i].toString();
                                }
                            }
                            return arguments;
                        }

                        @Override
                        public CachedClass[] getParameterTypes() {
                            final CachedClass[] paramTypes = method.getParameterTypes();
                            if (paramTypes.length > 0) {
                                return (CachedClass[]) GrailsArrayUtils.subarray(paramTypes, 1, paramTypes.length);
                            }
                            return paramTypes;
                        }
                    });
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private boolean isConstructorCallMethod(Method method) {
        return method != null && Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()) && method.getName().equals(CONSTRUCTOR_METHOD) && method.getParameterTypes().length>0;
    }

    private boolean isNotExcluded(Method method, final int modifiers) {
        final String name = method.getName();

        if (EXCLUDED_METHODS.contains(name)) return false;

        boolean isStatic = Modifier.isStatic(modifiers);

        // skip plain setters/getters by default for instance methods (non-static)
        if (!isStatic && (GrailsClassUtils.isSetter(name, method.getParameterTypes()) || GrailsNameUtils.isGetter(name, method.getReturnType(), method.getParameterTypes()))) {
            return false;
        }

        int minParameters = isStatic ? 0 : 1;

        return Modifier.isPublic(modifiers) &&
                !(method.isSynthetic() || method.isBridge()) &&
                !Modifier.isAbstract(modifiers) &&
                    !name.contains("$") &&
                      (method.getParameterTypes().length >= minParameters);
    }
}
