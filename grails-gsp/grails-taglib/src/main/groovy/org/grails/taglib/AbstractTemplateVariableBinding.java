/*
 * Copyright 2011 the original author or authors.
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
package org.grails.taglib;

import groovy.lang.Binding;

import java.util.*;

/**
 * Abstract super class for GroovyPage bindings
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractTemplateVariableBinding extends Binding {
    public AbstractTemplateVariableBinding() {
        super();
    }

    public AbstractTemplateVariableBinding(Map variables) {
        super(variables);
    }

    public AbstractTemplateVariableBinding(String[] args) {
        super(args);
    }

    public Map getVariablesMap() {
        return super.getVariables();
    }

    @SuppressWarnings("unchecked")
    public void setVariableDirectly(String name, Object value) {
        getVariablesMap().put(name, value);
    }

    public abstract Set<String> getVariableNames();

    @Override
    public Map getVariables() {
        return new TemplateVariableBindingMap(this);
    }

    public void addMap(Map additionalBinding) {
        for (Iterator<Map.Entry> i = additionalBinding.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry entry = i.next();
            String name = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            internalSetVariable(name, value);
        }
    }

    protected void internalSetVariable(String name, Object value) {
        setVariableDirectly(name, value);
    }

    public Binding findBindingForVariable(String name) {
        if (getVariablesMap().containsKey(name)) {
            return this;
        }
        return null;
    }

    public boolean isVariableCachingAllowed(String name) {
        return true;
    }

    protected static final class TemplateVariableBindingMap implements Map {
        AbstractTemplateVariableBinding binding;

        public TemplateVariableBindingMap(AbstractTemplateVariableBinding binding) {
            this.binding=binding;
        }

        public int size() {
            return binding.getVariableNames().size();
        }

        public boolean isEmpty() {
            return binding.getVariableNames().isEmpty();
        }

        public boolean containsKey(Object key) {
            return binding.getVariableNames().contains(key);
        }

        public boolean containsValue(Object value) {
            return values().contains(value);
        }

        public Object get(Object key) {
            return binding.getVariable(String.valueOf(key));
        }

        public Object put(Object key, Object value) {
            binding.setVariable(String.valueOf(key), value);
            return null;
        }

        public Object remove(Object key) {
            binding.setVariable(String.valueOf(key), null);
            return null;
        }

        public void putAll(Map m) {
            for (Object entryObj : m.entrySet()) {
                Map.Entry entry=(Map.Entry)entryObj;
                binding.setVariable(String.valueOf(entry.getKey()), entry.getValue());
            }
        }

        public void clear() {
            throw new UnsupportedOperationException("clear() not supported");
        }

        public Set keySet() {
            return binding.getVariableNames();
        }

        @SuppressWarnings("unchecked")
        public Collection values() {
            Set<String> variableNames = binding.getVariableNames();
            Collection values = new ArrayList(variableNames.size());
            for (String variable : variableNames) {
                values.add(binding.getVariable(variable));
            }
            return values;
        }

        public Set entrySet() {
            return Collections.unmodifiableSet(new AbstractSet() {
                @Override
                public Iterator iterator() {
                    return entryIterator();
                }

                @Override
                public int size() {
                    return binding.getVariableNames().size();
                }
            });
        }

        private Iterator entryIterator() {
            final Iterator iter = keySet().iterator();
            return new Iterator() {
                public boolean hasNext() {
                    return iter.hasNext();
                }
                public Object next() {
                    Object key = iter.next();
                    Object value = get(key);
                    return new BindingMapEntry(binding, key, value);
                }
                public void remove() {
                    throw new UnsupportedOperationException("remove() not supported");
                }
            };
        }
    }

    protected static class BindingMapEntry implements Map.Entry {
        private AbstractTemplateVariableBinding binding;

        private Object key;
        private Object value;
        protected BindingMapEntry(AbstractTemplateVariableBinding binding, Object key, Object value) {
            this.binding = binding;
            this.key = key;
            this.value = value;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object setValue(Object value) {
            String key = String.valueOf(getKey());
            Object oldValue = binding.getVariable(key);
            binding.setVariable(key, value);
            this.value = value;
            return oldValue;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Map.Entry)) {
                return false;
            }
            Map.Entry other = (Map.Entry) obj;
            return
                    (getKey() == null ? other.getKey() == null : getKey().equals(other.getKey())) &&
                            (getValue() == null ? other.getValue() == null : getValue().equals(other.getValue()));
        }

        @Override
        public int hashCode() {
            return (getKey() == null ? 0 : getKey().hashCode()) ^
                    (getValue() == null ? 0 : getValue().hashCode());
        }
    }
}
