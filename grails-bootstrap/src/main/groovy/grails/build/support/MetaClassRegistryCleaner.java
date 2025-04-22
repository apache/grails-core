/*
 * Copyright 2011 SpringSource
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
package grails.build.support;

import groovy.lang.*;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows clean-up of changes made to the MetaClassRegistry.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@SuppressWarnings("rawtypes")
public class MetaClassRegistryCleaner implements MetaClassRegistryChangeEventListener {

    private Map<Class, Object> alteredClasses = new ConcurrentHashMap<Class, Object> ();
    private Map<IdentityWeakReference, Object> alteredInstances = new ConcurrentHashMap<IdentityWeakReference, Object>();
    private static final Object NO_CUSTOM_METACLASS = new Object();
    private static boolean cleaning;
    private static final MetaClassRegistryCleaner INSTANCE = new MetaClassRegistryCleaner();

    private MetaClassRegistryCleaner() {
    }

    public static MetaClassRegistryCleaner createAndRegister() {

        MetaClassRegistry metaClassRegistry = GroovySystem.getMetaClassRegistry();
        MetaClassRegistryChangeEventListener[] listeners = metaClassRegistry.getMetaClassRegistryChangeEventListeners();
        boolean registered = false;
        for (MetaClassRegistryChangeEventListener listener : listeners) {
            if (listener == INSTANCE) {
                registered = true;break;
            }
        }
        if (!registered) {
            GroovySystem.getMetaClassRegistry().addMetaClassRegistryChangeEventListener(INSTANCE);
        }
        return INSTANCE;
    }

    public static void cleanAndRemove(MetaClassRegistryCleaner cleaner) {
        cleaner.clean();
        GroovySystem.getMetaClassRegistry().removeMetaClassRegistryChangeEventListener(cleaner);
    }

    public static void addAlteredMetaClass(Class cls, MetaClass altered) {
        INSTANCE.alteredClasses.put(cls, altered);
    }

    public void updateConstantMetaClass(MetaClassRegistryChangeEvent cmcu) {
        if (!cleaning) {
            MetaClass oldMetaClass = cmcu.getOldMetaClass();
            Class classToUpdate = cmcu.getClassToUpdate();
            Object instanceToUpdate = cmcu.getInstance();
            if (instanceToUpdate == null && (cmcu.getNewMetaClass() instanceof ExpandoMetaClass)) {
                updateMetaClassOfClass(oldMetaClass, classToUpdate);
            } else if (instanceToUpdate != null) {
                updateMetaClassOfInstance(oldMetaClass, instanceToUpdate);
            }
        }
    }

    private void updateMetaClassOfInstance(MetaClass oldMetaClass, Object instanceToUpdate) {
        IdentityWeakReference key = new IdentityWeakReference(instanceToUpdate);
        if (oldMetaClass != null) {
            Object current = alteredInstances.get(key);
            if (current == null || current == NO_CUSTOM_METACLASS) {
                alteredInstances.put(key, oldMetaClass);
            }
        } else {
            alteredInstances.put(key, NO_CUSTOM_METACLASS);
        }
    }

    private void updateMetaClassOfClass(MetaClass oldMetaClass, Class classToUpdate) {
        if (oldMetaClass != null && !(oldMetaClass.getClass().getName().equals("groovy.mock.interceptor.MockProxyMetaClass"))) {
            Object current = alteredClasses.get(classToUpdate);
            if (current == null ) {
                alteredClasses.put(classToUpdate, oldMetaClass);
            }
        }
        else {
            alteredClasses.put(classToUpdate, NO_CUSTOM_METACLASS);
        }
    }

    public synchronized void clean() {
        try {
            cleaning = true;
            MetaClassRegistryImpl registry = (MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry();
            cleanMetaClassOfClass(registry);
            cleanMetaClassOfInstance(registry);
        } finally {
            cleaning = false;
        }
    }

    private void cleanMetaClassOfInstance(MetaClassRegistryImpl registry) {
        List<IdentityWeakReference> keys = new ArrayList<IdentityWeakReference>(alteredInstances.keySet());
        for (IdentityWeakReference key : keys) {
            Object instance = key.get();
            if (instance != null) {
                Object alteredMetaClass = alteredInstances.get(key);
                if (alteredMetaClass == NO_CUSTOM_METACLASS) {
                    alteredMetaClass = null;
                }
                registry.setMetaClass(instance, (MetaClass) alteredMetaClass);
            }
        }
        alteredInstances.clear();
    }

    private void cleanMetaClassOfClass(MetaClassRegistryImpl registry) {
        Set<Class> classes = new HashSet<Class>(alteredClasses.keySet());
        for (Class aClass : classes) {
            Object alteredMetaClass = alteredClasses.get(aClass);
            if (alteredMetaClass == NO_CUSTOM_METACLASS) {
                registry.removeMetaClass(aClass);
            }
            else {
                registry.setMetaClass(aClass, (MetaClass) alteredMetaClass);
            }
        }
        alteredClasses.clear();
    }

    private static final class IdentityWeakReference extends WeakReference<Object> {

        private int hash;

        public IdentityWeakReference(Object referent) {
            super(referent);
            hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            return get() == ((IdentityWeakReference)obj).get();
        }
    }
}
