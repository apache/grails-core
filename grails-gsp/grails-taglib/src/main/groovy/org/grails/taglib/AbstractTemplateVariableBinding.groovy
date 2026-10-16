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
package org.grails.taglib

import groovy.transform.CompileStatic

/**
 * Abstract super class for GroovyPage bindings
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 */
@SuppressWarnings('rawtypes')
@CompileStatic
abstract class AbstractTemplateVariableBinding extends Binding {

    AbstractTemplateVariableBinding() {
        super()
    }

    AbstractTemplateVariableBinding(Map variables) {
        super(variables)
    }

    AbstractTemplateVariableBinding(String[] args) {
        super(args)
    }

    Map getVariablesMap() {
        return super.getVariables()
    }

    @SuppressWarnings('unchecked')
    void setVariableDirectly(String name, Object value) {
        getVariablesMap().put(name, value)
    }

    abstract Set<String> getVariableNames()

    @Override
    Map getVariables() {
        return new TemplateVariableBindingMap(this)
    }

    void addMap(Map additionalBinding) {
        for (Iterator<Map.Entry> i = additionalBinding.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = i.next()
            String name = String.valueOf(entry.getKey())
            Object value = entry.getValue()
            internalSetVariable(name, value)
        }
    }

    protected void internalSetVariable(String name, Object value) {
        setVariableDirectly(name, value)
    }

    Binding findBindingForVariable(String name) {
        if (getVariablesMap().containsKey(name)) {
            return this
        }
        return null
    }

    boolean isVariableCachingAllowed(String name) {
        return true
    }

    protected static final class TemplateVariableBindingMap implements Map {
        private final AbstractTemplateVariableBinding binding

        TemplateVariableBindingMap(AbstractTemplateVariableBinding binding) {
            this.binding = binding
        }

        int size() {
            return binding.getVariableNames().size()
        }

        boolean isEmpty() {
            return binding.getVariableNames().isEmpty()
        }

        boolean containsKey(Object key) {
            return binding.getVariableNames().contains(key)
        }

        boolean containsValue(Object value) {
            return values().contains(value)
        }

        Object get(Object key) {
            return binding.getVariable(String.valueOf(key))
        }

        Object put(Object key, Object value) {
            binding.setVariable(String.valueOf(key), value)
            return null
        }

        Object remove(Object key) {
            binding.setVariable(String.valueOf(key), null)
            return null
        }

        void putAll(Map m) {
            for (Object entryObj in m.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObj
                binding.setVariable(String.valueOf(entry.getKey()), entry.getValue())
            }
        }

        void clear() {
            throw new UnsupportedOperationException('clear() not supported')
        }

        Set keySet() {
            return binding.getVariableNames()
        }

        @SuppressWarnings('unchecked')
        Collection values() {
            Set<String> variableNames = binding.getVariableNames()
            Collection values = new ArrayList(variableNames.size())
            for (String variable in variableNames) {
                values.add(binding.getVariable(variable))
            }
            return values
        }

        Set entrySet() {
            return Collections.unmodifiableSet(new AbstractSet() {
                @Override
                Iterator iterator() {
                    return entryIterator()
                }

                @Override
                int size() {
                    return TemplateVariableBindingMap.this.@binding.getVariableNames().size()
                }
            })
        }

        private Iterator entryIterator() {
            final Iterator iter = keySet().iterator()
            return new Iterator() {

                boolean hasNext() {
                    return iter.hasNext()
                }

                Object next() {
                    Object key = iter.next()
                    Object value = TemplateVariableBindingMap.this.get(key)
                    return new BindingMapEntry(TemplateVariableBindingMap.this.@binding, key, value)
                }

                void remove() {
                    throw new UnsupportedOperationException('remove() not supported')
                }
            }
        }
    }

    protected static class BindingMapEntry implements Map.Entry {
        private AbstractTemplateVariableBinding binding

        private Object key
        private Object value

        protected BindingMapEntry(AbstractTemplateVariableBinding binding, Object key, Object value) {
            this.binding = binding
            this.key = key
            this.value = value
        }

        @Override
        Object getKey() {
            return key
        }

        @Override
        Object getValue() {
            return value
        }

        @Override
        Object setValue(Object value) {
            String key = String.valueOf(getKey())
            Object oldValue = binding.getVariable(key)
            binding.setVariable(key, value)
            this.value = value
            return oldValue
        }

        @Override
        boolean equals(Object obj) {
            if (this.is(obj)) {
                return true
            }
            if (!(obj instanceof Map.Entry)) {
                return false
            }
            Map.Entry other = (Map.Entry) obj
            return (getKey() == null ? other.getKey() == null : getKey().equals(other.getKey())) &&
                    (getValue() == null ? other.getValue() == null : getValue().equals(other.getValue()))
        }

        @Override
        int hashCode() {
            return (getKey() == null ? 0 : getKey().hashCode()) ^
                    (getValue() == null ? 0 : getValue().hashCode())
        }
    }
}
