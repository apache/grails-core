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
package grails.test.mixin

import java.time.LocalDate

import spock.lang.Specification
import spock.lang.Unroll

import grails.artefact.Artefact
import grails.gorm.validation.DefaultConstrainedProperty
import grails.testing.web.controllers.ControllerUnitTest
import grails.validation.Validateable
import grails.web.databinding.DataBindingUtils
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry

/**
 * Reproduces binding a dynamically constructed command whose superclass was enhanced as an action parameter.
 */
class InheritedCommandBindingSpec extends Specification implements ControllerUnitTest<InheritedCommandBindingController> {

    private static final LocalDate DATE_VALUE = LocalDate.of(2000, 1, 2)

    void setup() {
        grailsApplication.config.grails.databinding.denyByDefault = false
        params.putAll(requestValues())
    }

    void 'bindData binds subclass fields when only the superclass is an action parameter'() {
        when:
        def command = controller.bindDynamic().command

        then: 'the inherited and subclass properties are all bindable in compatibility mode'
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == DATE_VALUE
            enabled
        }
    }

    void 'DataBindingUtils also binds fields declared by the dynamically constructed subclass'() {
        given:
        def command = new InheritedBindingDynamicCommand()

        when:
        DataBindingUtils.bindObjectToInstance(command, requestValues())

        then:
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == DATE_VALUE
            enabled
        }
    }

    void 'an explicit include list can bind the same subclass fields'() {
        when:
        def command = controller.bindDynamicExplicitly().command

        then:
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == DATE_VALUE
            enabled
        }
    }

    void 'a subclass declared as an action parameter binds its own fields'() {
        when:
        def command = controller.bindDeclared().command

        then:
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == DATE_VALUE
            enabled
        }
    }

    void 'the same properties bind without an enhanced superclass'() {
        when:
        def command = controller.bindStandalone().command

        then:
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == DATE_VALUE
            enabled
        }
    }

    void 'an explicit include list still restricts subclass binding'() {
        when:
        def command = controller.bindDynamicRestricted().command

        then:
        command.baseValues == ['item-1']
        command.textValue == 'updated text'
        command.dateValue == null
        !command.enabled
    }

    void 'an empty explicit include list still binds no properties'() {
        when:
        def command = controller.bindDynamicEmpty().command

        then:
        command.baseValues == null
        command.textValue == null
        command.dateValue == null
        !command.enabled
    }

    @Unroll
    void 'bindable false remains enforced with secure=#secure and explicit includes=#explicit'() {
        given:
        grailsApplication.config.grails.databinding.denyByDefault = secure

        when:
        def command = explicit ? controller.bindProtectedFields().command : controller.bindDynamic().command

        then:
        command.protectedBaseValue == 'base value'
        command.protectedChildValue == 'child value'

        where:
        secure | explicit
        false  | false
        false  | true
        true   | false
        true   | true
    }

    void 'secure mode permits only explicitly bindable inherited and subclass properties'() {
        given:
        grailsApplication.config.grails.databinding.denyByDefault = true

        when:
        def command = controller.bindDynamic().command

        then:
        command.baseValues == ['item-1']
        command.textValue == 'updated text'
        command.dateValue == null
        !command.enabled
    }

    @Unroll
    void 'a subclass inheriting Validateable binds its own properties but not bindable false ones through #binding'() {
        when:
        def command = bindInheritedValidateable(binding)

        then: 'the constraints declared by the subclass itself still protect its properties'
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == DATE_VALUE
            enabled
            protectedBaseValue == 'base value'
            protectedChildValue == 'child value'
        }

        where:
        binding << ['bindData', 'DataBindingUtils']
    }

    @Unroll
    void 'secure mode binds the bindable true properties a subclass inheriting Validateable declares through #binding'() {
        given:
        grailsApplication.config.grails.databinding.denyByDefault = true

        when:
        def command = bindInheritedValidateable(binding)

        then:
        verifyAll(command) {
            baseValues == ['item-1']
            textValue == 'updated text'
            dateValue == null
            !enabled
            protectedBaseValue == 'base value'
            protectedChildValue == 'child value'
        }

        where:
        binding << ['bindData', 'DataBindingUtils']
    }

    @Unroll
    void 'each instance of a subclass inheriting an instance constraintsMap keeps its own bindable false properties through #binding'() {
        given: 'two instances whose inherited constraintsMap protects a different property'
        def protectsSecret = new InstanceConstraintsCommand(constraintsMap: unbindable('secret'))
        def protectsName = new InstanceConstraintsCommand(constraintsMap: unbindable('name'))

        when:
        bindInstanceConstraints(binding, protectsSecret)
        bindInstanceConstraints(binding, protectsName)

        then: 'the map each instance answers with decides what it binds'
        protectsSecret.name == 'changed'
        protectsSecret.secret == 'original secret'
        protectsName.name == 'original name'
        protectsName.secret == 'changed'

        where:
        binding << ['bindData', 'DataBindingUtils']
    }

    void 'switching modes does not reuse the other modes cached include list'() {
        expect:
        controller.bindDynamic().command.dateValue == DATE_VALUE

        when:
        grailsApplication.config.grails.databinding.denyByDefault = true

        then:
        controller.bindDynamic().command.dateValue == null

        when:
        grailsApplication.config.grails.databinding.denyByDefault = false

        then:
        controller.bindDynamic().command.dateValue == DATE_VALUE
    }

    private InheritedValidateableCommand bindInheritedValidateable(String binding) {
        if (binding == 'bindData') {
            return controller.bindInheritedValidateable().command
        }
        def command = new InheritedValidateableCommand()
        DataBindingUtils.bindObjectToInstance(command, requestValues())
        command
    }

    private void bindInstanceConstraints(String binding, InstanceConstraintsCommand command) {
        def values = [name: 'changed', secret: 'changed']
        if (binding == 'bindData') {
            controller.bindData(command, values)
        } else {
            DataBindingUtils.bindObjectToInstance(command, values)
        }
    }

    private static Map unbindable(String propertyName) {
        def constrainedProperty = new DefaultConstrainedProperty(
                InstanceConstraintsCommand, propertyName, String, new DefaultConstraintRegistry())
        constrainedProperty.addMetaConstraint('bindable', false)
        [(propertyName): constrainedProperty]
    }

    private static Map requestValues() {
        [baseValues: ['item-1'], textValue: 'updated text', dateValue: DATE_VALUE, enabled: true,
         protectedBaseValue: 'changed', protectedChildValue: 'changed']
    }
}

@Artefact('Controller')
class InheritedCommandBindingController {

    // Referencing the base type generates its binding metadata without enhancing a dynamically constructed subclass.
    def bindBase(InheritedBindingBaseCommand command) {
        [command: command]
    }

    def bindDynamic() {
        def command = new InheritedBindingDynamicCommand()
        bindData(command, params)
        [command: command]
    }

    def bindDynamicExplicitly() {
        def command = new InheritedBindingDynamicCommand()
        bindData(command, params, [include: ['baseValues', 'textValue', 'dateValue', 'enabled']])
        [command: command]
    }

    def bindDynamicRestricted() {
        def command = new InheritedBindingDynamicCommand()
        bindData(command, params, [include: ['baseValues', 'textValue']])
        [command: command]
    }

    def bindDynamicEmpty() {
        def command = new InheritedBindingDynamicCommand()
        bindData(command, params, [include: []])
        [command: command]
    }

    def bindProtectedFields() {
        def command = new InheritedBindingDynamicCommand()
        bindData(command, params, [include: ['protectedBaseValue', 'protectedChildValue']])
        [command: command]
    }

    def bindInheritedValidateable() {
        def command = new InheritedValidateableCommand()
        bindData(command, params)
        [command: command]
    }

    def bindDeclared(InheritedBindingDeclaredCommand command) {
        [command: command]
    }

    def bindStandalone() {
        def command = new InheritedBindingStandaloneCommand()
        bindData(command, params)
        [command: command]
    }
}

trait InheritedBindingNullable extends Validateable implements Serializable {
    static boolean defaultNullable() {
        true
    }
}

class InheritedBindingBaseCommand implements InheritedBindingNullable {
    List<String> baseValues
    String protectedBaseValue = 'base value'

    static constraints = {
        baseValues bindable: true
        protectedBaseValue bindable: false
    }
}

abstract class InheritedBindingIntermediateCommand extends InheritedBindingBaseCommand {
}

class InheritedBindingDynamicCommand extends InheritedBindingIntermediateCommand implements InheritedBindingNullable {
    String textValue
    LocalDate dateValue
    boolean enabled
    String protectedChildValue = 'child value'

    static constraints = {
        textValue bindable: true
        protectedChildValue bindable: false
    }
}

// Inherits Validateable, and with it the superclass's constraints accessor, without implementing it again.
class InheritedValidateableCommand extends InheritedBindingIntermediateCommand {
    String textValue
    LocalDate dateValue
    boolean enabled
    String protectedChildValue = 'child value'

    static constraints = {
        textValue bindable: true
        protectedChildValue bindable: false
    }
}

class InheritedBindingDeclaredCommand extends InheritedBindingIntermediateCommand implements InheritedBindingNullable {
    String textValue
    LocalDate dateValue
    boolean enabled
}

// Groovy generates an instance getConstraintsMap() for this property, which a subclass inherits.
class InstanceConstraintsBaseCommand {
    Map constraintsMap
}

class InstanceConstraintsCommand extends InstanceConstraintsBaseCommand {
    String name = 'original name'
    String secret = 'original secret'
}

class InheritedBindingStandaloneCommand implements InheritedBindingNullable {
    List<String> baseValues
    String textValue
    LocalDate dateValue
    boolean enabled
}
