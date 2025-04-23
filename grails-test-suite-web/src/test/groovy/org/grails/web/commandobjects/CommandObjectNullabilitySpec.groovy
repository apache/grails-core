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

package org.grails.web.commandobjects

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Issue
import spock.lang.Specification

class CommandObjectNullabilitySpec extends Specification implements ControllerUnitTest<CommandController> {

    @Issue('GRAILS-9686')
    void 'Test nullability'() {
        when:
        controller.createWidget()
        def widget = model.widget
        
        then:
        widget
        widget.hasErrors()
        widget.errors.errorCount == 5
        widget.errors.getFieldError('explicitlyNonNullableProperty').code == 'nullable'
        widget.errors.getFieldError('implicitlyNonNullableConstrainedProperty').code == 'nullable'
        widget.errors.getFieldError('implicitlyNonNullableUnconstrainedProperty').code == 'nullable'
        widget.errors.getFieldError('privatePropertyWithPublicGetterAndSetter').code == 'nullable'
        widget.errors.getFieldError('publicPropertyWithNoField').code == 'nullable'
        
        and:
        !widget.errors.getFieldError('staticPublicPropertyWithNoField')
        !widget.errors.getFieldError('someStaticProperty')
        !widget.errors.getFieldError('explicitlyNullableProperty')
        !widget.errors.getFieldError('privatePropertyWithNoSetterOrGetter')
    }

    @Issue('GRAILS-9686')
    void 'Test nullability inheritance'() {
        when:
        controller.createWidgetSubclass()
        def widget = model.widget
        
        then:
        widget
        widget.hasErrors()
        widget.errors.errorCount == 8
        widget.errors.getFieldError('explicitlyNonNullableProperty').code == 'nullable'
        widget.errors.getFieldError('implicitlyNonNullableConstrainedProperty').code == 'nullable'
        widget.errors.getFieldError('implicitlyNonNullableUnconstrainedProperty').code == 'nullable'
        widget.errors.getFieldError('subclassExplicitlyNonNullableProperty').code == 'nullable'
        widget.errors.getFieldError('subclassImplicitlyNonNullableConstrainedProperty').code == 'nullable'
        widget.errors.getFieldError('subclassImplicitlyNonNullableUnconstrainedProperty').code == 'nullable'
        widget.errors.getFieldError('privatePropertyWithPublicGetterAndSetter').code == 'nullable'
        widget.errors.getFieldError('publicPropertyWithNoField').code == 'nullable'
        
        and:
        !widget.errors.getFieldError('staticPublicPropertyWithNoField')
        !widget.errors.getFieldError('someStaticProperty')
        !widget.errors.getFieldError('explicitlyNullableProperty')
        !widget.errors.getFieldError('privatePropertyWithNoSetterOrGetter')
    }
}

@Artefact('Controller')
class CommandController {
    
    def createWidget(Widget widget) {
        render view: 'create', model: [widget: widget]
    }
    
    def createWidgetSubclass(WidgetSubclass widget) {
        render view: 'create', model: [widget: widget]
    }
}

class Widget {
    String explicitlyNullableProperty
    String explicitlyNonNullableProperty
    String implicitlyNonNullableConstrainedProperty
    String implicitlyNonNullableUnconstrainedProperty
    static String someStaticProperty
    private String privatePropertyWithNoSetterOrGetter
    private String privatePropertyWithPublicGetterAndSetter
    
    public void setPrivatePropertyWithPublicGetterAndSetter(String s) {
        privatePropertyWithPublicGetterAndSetter = s
    }
    
    public String getPrivatePropertyWithPublicGetterAndSetter() {
        privatePropertyWithPublicGetterAndSetter
    }
    
    public void setPublicPropertyWithNoField(String s) {
    }
    
    public String getPublicPropertyWithNoField() {
        null
    }

    public static void setStaticPublicPropertyWithNoField(String s) {
    }
    
    public static String getStaticPublicPropertyWithNoField() {
        null
    }

    static constraints = {
        explicitlyNullableProperty nullable: true
        explicitlyNonNullableProperty nullable: false
        implicitlyNonNullableConstrainedProperty matches: /[A-Z].*/
    }
}

class WidgetSubclass extends Widget {
    String subclassExplicitlyNullableProperty
    String subclassExplicitlyNonNullableProperty
    String subclassImplicitlyNonNullableConstrainedProperty
    String subclassImplicitlyNonNullableUnconstrainedProperty
    static String someSubclassStaticProperty
    
    static constraints = {
        subclassExplicitlyNullableProperty nullable: true
        subclassExplicitlyNonNullableProperty nullable: false
        subclassImplicitlyNonNullableConstrainedProperty matches: /[A-Z].*/
    }
}
