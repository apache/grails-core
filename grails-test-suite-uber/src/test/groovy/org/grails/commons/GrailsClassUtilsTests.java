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
package org.grails.commons;

import grails.util.GrailsClassUtils;
import grails.util.GrailsNameUtils;
import junit.framework.TestCase;
import org.junit.jupiter.api.Test;
import spock.lang.Issue;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Graeme Rocher
 */
public class GrailsClassUtilsTests {

    @Test
    public void testFindPropertyNameForValue() {
        TestBean testBean = new TestBean();

        assertEquals("userName", GrailsClassUtils.findPropertyNameForValue(testBean, testBean.getUserName()));
        assertEquals("welcomeMessage", GrailsClassUtils.findPropertyNameForValue(testBean, TestBean.getWelcomeMessage()));
    }

    @Test
    public void testBooleanMatchesboolean() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Boolean.class, boolean.class));
    }

    @Test
    public void testbooleanMatchesBoolean() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(boolean.class, Boolean.class));
    }

    @Test
    public void testIntegerMatchesint() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Integer.class, int.class));
    }

    @Test
    public void testintMatchesInteger() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(int.class, Integer.class));
    }

    @Test
    public void testShortMatchesshort() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Short.class, short.class));
    }

    @Test
    public void testshortMatchesShort() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(short.class, Short.class));
    }

    @Test
    public void testByteMatchesbyte() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Byte.class, byte.class));
    }

    @Test
    public void testbyteMatchesByte() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(byte.class, Byte.class));
    }

    @Test
    public void testCharacterMatcheschar() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Character.class, char.class));
    }

    @Test
    public void testcharMatchesCharacter() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(char.class, Character.class));
    }

    @Test
    public void testLongMatcheslong() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Long.class, long.class));
    }

    @Test
    public void testlongMatchesLong() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(long.class, Long.class));
    }

    @Test
    public void testFloatMatchesfloat() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Float.class, float.class));
    }

    @Test
    public void testfloatMatchesFloat() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(float.class, Float.class));
    }

    @Test
    public void testDoubleMatchesdouble() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(Double.class, double.class));
    }

    @Test
    public void testdoubleMatchesDouble() {
        assertTrue(GrailsClassUtils.isMatchBetweenPrimativeAndWrapperTypes(double.class, Double.class));
    }

    @Test
    public void testAssignableFromOrPrimitiveCompatible() {
        assertTrue(GrailsClassUtils.isGroovyAssignableFrom(double.class, Double.class));
        assertTrue(GrailsClassUtils.isGroovyAssignableFrom(Integer.class, int.class));
        assertTrue(GrailsClassUtils.isGroovyAssignableFrom(Number.class, int.class));
    }

    @Test
    public void testGetterNames() {
        assertEquals("getConstraints", GrailsClassUtils.getGetterName("constraints"));
        assertEquals("getURL", GrailsClassUtils.getGetterName("URL"));
        assertEquals("getUrl", GrailsClassUtils.getGetterName("Url"));
    }

    @Test
    public void testIsGetterOrSetter() {
        assertTrue(GrailsClassUtils.isSetter("setSomething", new Class[] { String.class }));
        assertTrue(GrailsNameUtils.isGetter("getSomething", new Class[0]));
        assertTrue(GrailsNameUtils.isGetter("isSomething", new Class[0]));
        assertTrue(GrailsClassUtils.isSetter("setURL", new Class[] { String.class }));
        assertTrue(GrailsNameUtils.isGetter("getURL", new Class[0]));
        assertTrue(GrailsNameUtils.isGetter("isURL", new Class[0]));
        assertTrue(GrailsClassUtils.isSetter("setaProp", new Class[] { String.class }));
        assertTrue(GrailsNameUtils.isGetter("getaProp", new Class[0]));
        assertTrue(GrailsNameUtils.isGetter("isaProp", new Class[0]));
        assertTrue(GrailsClassUtils.isSetter("setX", new Class[] { String.class }));
        assertTrue(GrailsNameUtils.isGetter("getX", new Class[0]));
        assertTrue(GrailsNameUtils.isGetter("isX", new Class[0]));
        assertTrue(GrailsClassUtils.isSetter("setX2", new Class[] { String.class }));
        assertTrue(GrailsNameUtils.isGetter("getX2", new Class[0]));
        assertTrue(GrailsNameUtils.isGetter("isX2", new Class[0]));

        assertFalse(GrailsNameUtils.isGetter("something", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("get", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("set", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("somethingElse", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("setSomething", new Class[] { String.class, Object.class }));
        assertFalse(GrailsNameUtils.isGetter("getSomething", new Class[] { Object.class }));

        assertFalse(GrailsNameUtils.isGetter("getsomething", new Class[0]));
        assertFalse(GrailsNameUtils.isGetter("issomething", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("setsomething", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("get0", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("set0", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("get2other", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("set2other", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("getq3", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("setq3", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("get5A", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("set5A", new Class[] { String.class }));
        assertFalse(GrailsNameUtils.isGetter("", new Class[0]));
        assertFalse(GrailsClassUtils.isSetter("", new Class[] { String.class }));

        assertFalse(GrailsNameUtils.isGetter(null, new Class[] { Object.class }));
        assertFalse(GrailsNameUtils.isGetter("getSomething", null));
        assertFalse(GrailsNameUtils.isGetter(null, null));
    }

    @Test
    public void testGetPropertyForGetter() {
        assertEquals("something", GrailsNameUtils.getPropertyForGetter("getSomething"));
        assertEquals("URL", GrailsNameUtils.getPropertyForGetter("getURL"));
        assertEquals("p", GrailsNameUtils.getPropertyForGetter("getP"));
        assertEquals("URL", GrailsNameUtils.getPropertyForGetter("isURL"));
        assertEquals("aProp", GrailsNameUtils.getPropertyForGetter("getaProp"));
        assertEquals("x2", GrailsNameUtils.getPropertyForGetter("getX2"));
        assertEquals("x2", GrailsNameUtils.getPropertyForGetter("isX2"));
        assertEquals("_someProperty", GrailsClassUtils.getPropertyForGetter("get_someProperty", String.class));

        assertNull(GrailsNameUtils.getPropertyForGetter(null));
        assertNull(GrailsNameUtils.getPropertyForGetter(""));
        assertNull(GrailsNameUtils.getPropertyForGetter("get0"));
        assertNull(GrailsNameUtils.getPropertyForGetter("get2other"));
        assertNull(GrailsNameUtils.getPropertyForGetter("getq3"));
        assertNull(GrailsNameUtils.getPropertyForGetter("get5A"));
        assertNull(GrailsNameUtils.getPropertyForGetter("setSomething"));
        assertNull(GrailsNameUtils.getPropertyForGetter("getit"));
        assertNull(GrailsNameUtils.getPropertyForGetter("geta"));
        assertNull(GrailsNameUtils.getPropertyForGetter("get0"));
    }

    @Test
    public void testGetStaticField() {
        assertEquals("SomeFieldValue",
                GrailsClassUtils.getStaticFieldValue(ClassWithStaticFieldAndStaticPropertyWithSameName.class, "name"));
        assertEquals("SomePropertyValue",
                GrailsClassUtils.getStaticPropertyValue(ClassWithStaticFieldAndStaticPropertyWithSameName.class, "name"));
    }

    @Test
    public void testGetStaticProperty() {
        assertEquals(HttpServletRequest.BASIC_AUTH,
                GrailsClassUtils.getStaticPropertyValue(HttpServletRequest.class, "BASIC_AUTH"));

        assertEquals("hello", GrailsClassUtils.getStaticPropertyValue(TestBean.class, "welcomeMessage"));
    }

    @Test
    public void testIsPublicStatic() throws Exception {
        assertTrue(GrailsClassUtils.isPublicStatic(HttpServletRequest.class.getDeclaredField("BASIC_AUTH")));

        assertFalse(GrailsClassUtils.isPublicStatic(String.class.getDeclaredField("serialVersionUID")));

        assertFalse(GrailsClassUtils.isPublicStatic(TestBean.class.getDeclaredField("welcomeMessage")));
    }

    @Test
    public void testGetPropertyOrStatic() {
        TestBean bean = new TestBean();
        assertEquals("hello", GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(bean, "welcomeMessage"));
        assertEquals("marc", GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(bean, "userName"));
        assertEquals("indian", GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(bean, "favouriteFood"));
        assertEquals("Cardiacs", GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(bean, "favouriteArtist"));
    }

    @Test
    public void testGetFieldValue() {
        TestBean bean = new TestBean();

        assertTrue(GrailsClassUtils.isPublicField(bean, "favouriteArtist"));

        assertEquals("Cardiacs", GrailsClassUtils.getFieldValue(bean, "favouriteArtist"));
    }

    @Test
    public void testIsAssignableOrConvertibleFrom() {

        // test number
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, int.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, Integer.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, short.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, Short.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, byte.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, Byte.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, long.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, Long.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, float.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, Float.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, double.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Number.class, Double.class));

        // test integer
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, int.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, Integer.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, Short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, Byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, Long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, Float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, double.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, Double.class));

        // test short
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, int.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, Integer.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, short.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, Short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, Byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, Long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, Float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, double.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Short.class, Double.class));

        // test byte
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, int.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, Integer.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, Short.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, byte.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, Byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, Long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, Float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, double.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Byte.class, Double.class));

        // test long
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, int.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, Integer.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, Short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, Byte.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, long.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, Long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, Float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, double.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Long.class, Double.class));

        // test float
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, int.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, Integer.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, Short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, Byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, Long.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, float.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, Float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, double.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Float.class, Double.class));

        // test double
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, int.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, Integer.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, Short.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, Byte.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, Long.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, float.class));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, Float.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, double.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Double.class, Double.class));

        // test boolean
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Boolean.class, boolean.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Boolean.class, Boolean.class));

        // test character
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Character.class, char.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Character.class, Character.class));

        // test object
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, int.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Integer.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, short.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Short.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, byte.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Byte.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, long.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Long.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, float.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Float.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, double.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Double.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, boolean.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Boolean.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, char.class));
        assertTrue(GrailsClassUtils.isAssignableOrConvertibleFrom(Object.class, Character.class));

        // test null
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(null, null));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(Integer.class, null));
        assertFalse(GrailsClassUtils.isAssignableOrConvertibleFrom(null, int.class));
    }

    @Test
    public void testIsPropertyGetter() throws Exception {
        assertTrue(GrailsClassUtils.isPropertyGetter(ClassHavingPropertyGetters.class.getDeclaredMethod("getName")));
        assertFalse(GrailsClassUtils.isPropertyGetter(ClassHavingPropertyGetters.class.getDeclaredMethod("setName")));
        assertFalse(GrailsClassUtils.isPropertyGetter(ClassHavingPropertyGetters.class.getDeclaredMethod("getSurname")));
        assertFalse(GrailsClassUtils.isPropertyGetter(ClassHavingPropertyGetters.class.getDeclaredMethod("getNewYear")));
        assertFalse(GrailsClassUtils.isPropertyGetter(ClassHavingPropertyGetters.class.getDeclaredMethod("getFilename", String.class)));
        assertFalse(GrailsClassUtils.isPropertyGetter(ClassHavingPropertyGetters.class.getDeclaredMethod("getTitle")));
    }

    @Test
    @Issue("https://github.com/apache/grails-core/issues/10343")
    public void testPropertiesBeginningWithSingleLowerCaseLetter() throws Exception {
        assertTrue(GrailsClassUtils.isPropertyGetter(SomeGroovyClass.class.getDeclaredMethod("getaString")));
        assertTrue(GrailsClassUtils.isPropertyGetter(SomeGroovyClass.class.getDeclaredMethod("isaBoolean")));
        assertTrue(GrailsClassUtils.isPropertyGetter(SomeGroovyClass.class.getDeclaredMethod("getS")));
        assertTrue(GrailsClassUtils.isPropertyGetter(SomeGroovyClass.class.getDeclaredMethod("isB")));
    }
}

class ClassWithStaticFieldAndStaticPropertyWithSameName {
    public static String name = "SomeFieldValue";

    public static String getName() {
        return "SomePropertyValue";
    }
}

class ClassHavingPropertyGetters {
    public String getName() { return ""; }

    public void setName() {  }

    protected String getSurname() { return ""; }

    private Date getNewYear() { return null; }

    public String getFilename(String prefix) { return ""; }

    public static String getTitle() { return ""; }
}
