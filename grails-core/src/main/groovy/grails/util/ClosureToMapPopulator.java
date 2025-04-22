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
package grails.util;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple class that takes method invocations and property setters and populates
 * the arguments of these into the supplied map ignoring null values.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class ClosureToMapPopulator extends GroovyObjectSupport {

    private Map map;

    public ClosureToMapPopulator(Map theMap) {
        map = theMap;
    }

    public ClosureToMapPopulator() {
        this(new HashMap());
    }

    public Map populate(Closure callable) {
        callable.setDelegate(this);
        callable.setResolveStrategy(Closure.DELEGATE_FIRST);
        callable.call();
        return map;
    }

    @Override
    public void setProperty(String name, Object o) {
        if (o != null) {
            map.put(name, o);
        }
    }

    @Override
    public Object invokeMethod(String name, Object o) {
        if (o != null) {
            if (o.getClass().isArray()) {
                Object[] args = (Object[])o;
                if (args.length == 1) {
                    map.put(name, args[0]);
                }
                else {
                    map.put(name, Arrays.asList(args));
                }
            }
            else {
                map.put(name,o);
            }
        }
        return null;
    }
}
