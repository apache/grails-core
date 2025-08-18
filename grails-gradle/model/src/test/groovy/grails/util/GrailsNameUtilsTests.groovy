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
package grails.util

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Test case for {@link GrailsNameUtils}.
 */
class GrailsNameUtilsTests {

    @Test
    void testGetFullClassName() {
        assertEquals("FooBar", GrailsNameUtils.getFullClassName('FooBar$$CGlib'))
    }

    @Test
    void testIsValidPackage() {
        assertTrue(GrailsNameUtils.isValidJavaPackage("jax.demo.bar"))
        assertFalse(GrailsNameUtils.isValidJavaPackage("jax.demo.2015"))
    }

    @Test
    void testGetGetterNameForPropertyThatBeginsWithASingleLowerCaseLetter() {
        assertEquals("getaPaperback", GrailsNameUtils.getGetterName("aPaperback"))
        assertEquals("setaPaperback", GrailsNameUtils.getSetterName("aPaperback"))
    }

    @Test
    void testGetClassNameRepresentation() {
        assertEquals("MyClass", GrailsNameUtils.getClassNameRepresentation("my-class"))
        assertEquals("MyClass", GrailsNameUtils.getClassNameRepresentation("MyClass"))
        assertEquals("F", GrailsNameUtils.getClassNameRepresentation(".f"))
        assertEquals("AB", GrailsNameUtils.getClassNameRepresentation(".a.b"))
        assertEquals("AlphaBakerCharlie", GrailsNameUtils.getClassNameRepresentation(".alpha.baker.charlie"))
    }

    @Test
    void testGetPropertyNameRepresentation() {
        assertEquals("bar", GrailsNameUtils.getPropertyNameRepresentation("bar"))
        assertEquals("f", GrailsNameUtils.getPropertyNameRepresentation(".f"))
        assertEquals("b", GrailsNameUtils.getPropertyNameRepresentation(".a.b"))
        assertEquals("charlie", GrailsNameUtils.getPropertyNameRepresentation(".alpha.baker.charlie"))
        assertEquals("", GrailsNameUtils.getPropertyNameRepresentation(".a.b."))
    }

    @Test
    void testGetNaturalName() {
        assertEquals("First Name", GrailsNameUtils.getNaturalName("firstName"))
        assertEquals("URL", GrailsNameUtils.getNaturalName("URL"))
        assertEquals("Local URL", GrailsNameUtils.getNaturalName("localURL"))
        assertEquals("URL local", GrailsNameUtils.getNaturalName("URLlocal"))
        assertEquals("A URL local", GrailsNameUtils.getNaturalName("aURLlocal"))
        assertEquals("My Domain Class", GrailsNameUtils.getNaturalName("MyDomainClass"))
        assertEquals("My Domain Class", GrailsNameUtils.getNaturalName("com.myco.myapp.MyDomainClass"))
        assertEquals("A Name", GrailsNameUtils.getNaturalName("aName"))
    }

    @Test
    void testGetLogicalName() {
        assertEquals("Test", GrailsNameUtils.getLogicalName("TestController", "Controller"))
        assertEquals("Test", GrailsNameUtils.getLogicalName("org.music.TestController", "Controller"))
    }

    @Test
    void testGetLogicalPropertyName() {
        assertEquals("myFunky", GrailsNameUtils.getLogicalPropertyName("MyFunkyController", "Controller"))
        assertEquals("HTML", GrailsNameUtils.getLogicalPropertyName("HTMLCodec", "Codec"))
        assertEquals("payRoll", GrailsNameUtils.getLogicalPropertyName("org.something.PayRollController", "Controller"))
    }

    @Test
    void testGetLogicalPropertyNameForArtefactWithSingleCharacterName() {
        assertEquals("a", GrailsNameUtils.getLogicalPropertyName("AController", "Controller"))
        assertEquals("b", GrailsNameUtils.getLogicalPropertyName("BService", "Service"))
    }

    @Test
    void testGetLogicalPropertyNameForArtefactWithAllUpperCaseName() {
        assertEquals("ABC", GrailsNameUtils.getLogicalPropertyName("ABCController", "Controller"))
        assertEquals("BCD", GrailsNameUtils.getLogicalPropertyName("BCDService", "Service"))
    }

    @Test
    void testGetScriptName() {
        assertEquals("grails-name-utils-tests", GrailsNameUtils.getScriptName(getClass()))
        assertEquals("", GrailsNameUtils.getScriptName(""))
        assertNull(GrailsNameUtils.getScriptName((String) null))
        assertNull(GrailsNameUtils.getScriptName((Class<?>) null))
    }

    @Test
    void testGetNameFromScript() {
        assertEquals("GrailsClassUtilsTests", GrailsNameUtils.getNameFromScript("grails-class-utils-tests"))
        assertEquals("Grails", GrailsNameUtils.getNameFromScript("grails"))
        assertEquals("CreateApp", GrailsNameUtils.getNameFromScript("create-app"))
        assertEquals("", GrailsNameUtils.getNameFromScript(""))
        assertNull(GrailsNameUtils.getNameFromScript(null))
    }

    @Test
    void testGetPluginName() {
        assertEquals("db-utils", GrailsNameUtils.getPluginName("DbUtilsGrailsPlugin.groovy"))
        assertEquals("shiro", GrailsNameUtils.getPluginName("ShiroGrailsPlugin.groovy"))
        // The following isn't supported yet - but it should be.
//        assertEquals("CAS-security", GrailsNameUtils.getPluginName("CASSecurityGrailsPlugin.groovy"))
        assertEquals("", GrailsNameUtils.getPluginName(""))
        assertNull(GrailsNameUtils.getPluginName(null))

        try {
            GrailsNameUtils.getPluginName("NotAPlugin.groovy")
            fail("GrailsNameUtils.getPluginName() should have thrown an IllegalArgumentException.")
        }
        catch (IllegalArgumentException ex) {
            // Expected!
        }
    }

    @Test
    void testIsBlank() {
        assertTrue(GrailsNameUtils.isBlank(null), "'null' value should count as blank.")
        assertTrue(GrailsNameUtils.isBlank(""), "Empty string should count as blank.")
        assertTrue(GrailsNameUtils.isBlank("  "), "Spaces should count as blank.")
        assertTrue(GrailsNameUtils.isBlank("\t"), "A tab should count as blank.")
        assertFalse(GrailsNameUtils.isBlank("\t  h"), "String with whitespace and non-whitespace should not count as blank.")
        assertFalse(GrailsNameUtils.isBlank("test"), "String should not count as blank.")
    }
}
