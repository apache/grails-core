/*
 * Copyright 2011 SpringSource.
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
package grails.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Burt Beckwith
 * @since 2.0
 */
public class Holder<T> {

    private Map<Integer, T> instances = new ConcurrentHashMap<Integer, T>();
    // TODO remove mappedOnly and singleton
    private T singleton;
    private String name;

    public Holder(String name) {
        this.name = name;
    }

    public T get() {
        return get(false);
    }

    public T get(boolean mappedOnly) {
        T t = instances.get(getClassLoaderId());
        if (t != null) {
            return t;
        }

        t = lookupSecondary();
        if (t != null) {
            return t;
        }

//        t = instances.get(System.identityHashCode(getClass().getClassLoader()));
        if (!mappedOnly) {
            t = singleton;
        }
        return t;
    }

    protected T lookupSecondary() {
        // override in subclass if needed
        return null;
    }

    public void set(T t) {
        int id = getClassLoaderId();
        int thisClassLoaderId = System.identityHashCode(getClass().getClassLoader());
        if (t == null) {
            instances.remove(id);
            instances.remove(thisClassLoaderId);
        }
        else {
            instances.put(id, t);
            instances.put(thisClassLoaderId, t);
        }
        singleton = t;
    }

    private int getClassLoaderId() {
        return Environment.isWarDeployed() ? System.identityHashCode(Thread.currentThread().getContextClassLoader()) : System.identityHashCode(getClass().getClassLoader());
    }
}
