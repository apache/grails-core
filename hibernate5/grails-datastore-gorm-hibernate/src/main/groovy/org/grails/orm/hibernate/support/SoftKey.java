/*
 * Copyright 2013 the original author or authors.
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
package org.grails.orm.hibernate.support;

import java.lang.ref.SoftReference;

/**
 * SoftReference key to be used with ConcurrentHashMap.
 *
 * @author Lari Hotari
 */
public class SoftKey<T> extends SoftReference<T> {
    final int hash;

    public SoftKey(T referent) {
        super(referent);
        hash = referent.hashCode();
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
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        @SuppressWarnings("unchecked")
        SoftKey<T> other = (SoftKey<T>)obj;
        if (hash != other.hash) {
            return false;
        }
        T referent = get();
        T otherReferent = other.get();
        if (referent == null) {
            if (otherReferent != null) {
                return false;
            }
        }
        else if (!referent.equals(otherReferent)) {
            return false;
        }
        return true;
    }
}
