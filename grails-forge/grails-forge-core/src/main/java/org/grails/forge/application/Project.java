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
package org.grails.forge.application;

import org.grails.forge.util.NameUtils;

import java.util.HashMap;
import java.util.Map;

public class Project extends ProjectIdentifier {

    private static final String PACKAGE_NAME = "packageName";
    private static final String PACKAGE_PATH = "packagePath";
    private static final String CLASS_NAME = "className";
    private static final String NATURAL_NAME = "naturalName";
    private static final String PROPERTY_NAME = "propertyName";
    private static final String NAME = "name";
    private final String packagePath;
    private final String className;
    private final String naturalName;
    private final String propertyName;

    public Project(String packageName,
                   String packagePath,
                   String className,
                   String naturalName,
                   String propertyName,
                   String name) {
        super(packageName, name);
        this.packagePath = packagePath;
        this.className = className;
        this.naturalName = naturalName;
        this.propertyName = propertyName;
    }

    public String getPackagePath() {
        return packagePath;
    }

    public String getClassName() {
        return className;
    }

    public String getNaturalName() {
        return naturalName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(PACKAGE_NAME, getPackageName());
        properties.put(PACKAGE_PATH, packagePath);
        properties.put(CLASS_NAME, className);
        properties.put(NATURAL_NAME, naturalName);
        properties.put(PROPERTY_NAME, propertyName);
        properties.put(NAME, getName());
        return properties;
    }

    /**
     * A new project wth the given class name.
     * @param className The class name
     * @return The new project.
     */
    public Project withClassName(String className) {
        return new Project(
                getPackageName(),
                packagePath,
                className,
                naturalName,
                NameUtils.getPropertyName(className),
                getName()
        );
    }
}
