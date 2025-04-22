/* Copyright 2004-2005 the original author or authors.
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
package org.grails.web.servlet;

import grails.util.GrailsWebMockUtil;
import grails.web.mvc.FlashScope;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Graeme Rocher
 */
@SuppressWarnings({"unchecked","rawtypes"})
public class GrailsFlashScopeTests {

    private static final String ERRORS_PROPERTY = "errors";

    @Test
    public void testPutNull() {
        GrailsWebMockUtil.bindMockWebRequest();

        FlashScope fs = new GrailsFlashScope();
        fs.put("test",null);
    }

    @Test
    public void testNextState() {

        GrailsWebMockUtil.bindMockWebRequest();

        FlashScope fs = new GrailsFlashScope();
        fs.put("test","value");
        fs.put("fred","flintstone");
        fs.getNow().put("barney", "rubble");

        assertFalse(fs.isEmpty());
        assertEquals("flintstone",fs.get("fred"));
        assertEquals("rubble",fs.get("barney"));
        assertEquals(3, fs.size());
        assertTrue(fs.containsKey("test"));
        assertTrue(fs.containsKey("barney"));
        assertTrue(fs.containsValue("value"));
        assertFalse(fs.containsKey("wilma"));

        // the state immediately following this one the map should still contain the previous entries
        fs.next();

        assertFalse(fs.isEmpty());
        assertEquals("flintstone",fs.get("fred"));
        assertEquals(2, fs.size());
        assertTrue(fs.containsKey("test"));
        assertTrue(fs.containsValue("value"));
        assertFalse(fs.containsKey("wilma"));

        // the next state it should be empty
        fs.next();

        assertTrue(fs.isEmpty());
        assertEquals(0,fs.size());
    }

    /**
     * Bug: GRAILS-3083
     */
    @Test
    public void testPutMap() {

        GrailsWebMockUtil.bindMockWebRequest();

        //set up a map with ERRORS_PROPERTY
        Map map = new HashMap();
        StringWithError value = new StringWithError("flinstone");
        map.put("fred",value);
        map.put("barney","rabble");
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(value.getClass());
        mc.setProperty(value, ERRORS_PROPERTY, new Object());

        //put the map to scope
        FlashScope fs = new GrailsFlashScope();
        fs.put("test", "value");
        fs.put("flinstones", map);

        assertFalse(fs.isEmpty());
        assertEquals(2, fs.size());
        assertEquals(map,fs.get("flinstones"));
        assertEquals("value", fs.get("test"));

        // the state immediately following this one the map should still contain the previous entries
        fs.next();

        assertFalse(fs.isEmpty());
        assertEquals(2, fs.size());
        assertEquals(map,fs.get("flinstones"));
        assertEquals("value", fs.get("test"));

        // the next state it should be empty
        fs.next();

        assertTrue(fs.isEmpty());
        assertEquals(0,fs.size());
    }

    private class StringWithError {
        private String value;
        private Object errors;

        public StringWithError(String value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        public Object getErrors() {
            return errors;
        }

        @SuppressWarnings("unused")
        public void setErrors(Object errors) {
            this.errors = errors;
        }

        @Override
        public boolean equals(Object obj) {
            if (value == null) {
                return (obj == null);
            }

            return value.equals(obj);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
