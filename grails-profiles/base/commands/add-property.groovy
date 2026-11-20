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

import grails.codegen.model.AbstractMemberDefinition
import grails.codegen.model.DomainFieldModifier
import grails.codegen.model.PropertyDefinition
import org.grails.cli.interactive.completers.DomainClassCompleter

description("Adds a property to an existing domain class") {
    usage "grails add-property [DOMAIN CLASS] [PROPERTY:TYPE]"
    argument name: 'Domain Class', description: "The name of the domain class", required: true
    argument name: 'Property Spec', description: "Property specification in name:Type format (e.g., title:String)", required: true
    completer DomainClassCompleter
    flag name: 'nullable', description: "Mark the property as nullable"
    flag name: 'not-nullable', description: "Mark the property as NOT nullable (generates @NotNull)"
    flag name: 'blank', description: "Allow blank values (String properties only)"
    flag name: 'not-blank', description: "Disallow blank values (generates @NotBlank)"
    flag name: 'max-size', description: "Maximum size constraint (String properties only)"
    flag name: 'min-size', description: "Minimum size constraint (String properties only)"
    flag name: 'constraint-style', description: "Constraint style: grails (default), jakarta, or both"
}

if (args.size() < 2) {
    error "Usage: grails add-property [DOMAIN CLASS] [PROPERTY:TYPE]"
    error "Example: grails add-property Book title:String --nullable"
    return false
}

String domainClassName = args[0]
String propertySpec = args[1]

PropertyDefinition property
try {
    property = PropertyDefinition.parse(propertySpec)
} catch (IllegalArgumentException e) {
    error "Invalid property specification: ${e.message}"
    return false
}

def nullableFlag = flag('nullable')
def notNullableFlag = flag('not-nullable')
if (nullableFlag != null) {
    property.nullable = true
} else if (notNullableFlag != null) {
    property.nullable = false
}

def blankFlag = flag('blank')
def notBlankFlag = flag('not-blank')
if (blankFlag != null) {
    property.blank = true
} else if (notBlankFlag != null) {
    property.blank = false
}

def maxSizeFlag = flag('max-size')
if (maxSizeFlag != null) {
    try {
        property.maxSize = maxSizeFlag instanceof Integer ? maxSizeFlag : Integer.parseInt(maxSizeFlag.toString())
    } catch (NumberFormatException e) {
        error "Invalid max-size value: ${maxSizeFlag}"
        return false
    }
}

def minSizeFlag = flag('min-size')
if (minSizeFlag != null) {
    try {
        property.minSize = minSizeFlag instanceof Integer ? minSizeFlag : Integer.parseInt(minSizeFlag.toString())
    } catch (NumberFormatException e) {
        error "Invalid min-size value: ${minSizeFlag}"
        return false
    }
}

def constraintStyleFlag = flag('constraint-style')
if (constraintStyleFlag != null) {
    try {
        property.constraintStyle = AbstractMemberDefinition.ConstraintStyle.valueOf(constraintStyleFlag.toString().toUpperCase())
    } catch (IllegalArgumentException e) {
        error "Invalid constraint-style value: ${constraintStyleFlag}. Use: grails, jakarta, or both"
        return false
    }
}

try {
    property.validate()
} catch (IllegalArgumentException e) {
    error "Invalid property definition: ${e.message}"
    return false
}

def sourceClass = source(domainClassName)
if (!sourceClass) {
    error "Domain class not found: ${domainClassName}"
    error "Run 'grails create-domain-class ${domainClassName}' first."
    return false
}

def domainFile = sourceClass.file
def modifier = new DomainFieldModifier()

if (modifier.memberExists(domainFile, property.name)) {
    error "Property '${property.name}' already exists in ${domainClassName}"
    return false
}

try {
    modifier.addProperty(domainFile, property)
    addStatus "Added property '${property.name}' to ${projectPath(sourceClass)}"
} catch (Exception e) {
    error "Failed to add property: ${e.message}"
    return false
}

return true
