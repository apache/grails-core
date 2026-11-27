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
package org.grails.web.binding

import java.nio.charset.StandardCharsets

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.lang.TempDir

import grails.artefact.Artefact
import grails.web.databinding.DataBindingUtils
import org.grails.web.databinding.DatabindingWhiteListASTTransformation
import org.grails.web.databinding.DefaultASTDatabindingHelper
import org.grails.web.databinding.GeneratedDatabindingWhiteList

/**
 * A class whose parent is already compiled, as a parent in another module, in a plugin, or in an incremental build
 * is, must get the generated white lists it gets when it is compiled together with its parent. The compiled parent
 * has no constraints closure and no transients list to read, so what the parent declared transient or
 * {@code bindable: false} must not become bindable in the subclass; and what the parent gets from a transformation
 * such as {@code @Delegate} must stay bindable. A class bound by rules, its own or inherited, gets its white lists
 * wherever it is compiled, so a command object compiled apart from the controller that binds it is bound by the
 * rules it declares all the same.
 */
class DefaultASTDatabindingHelperCompiledParentSpec extends Specification {

    @TempDir
    File tempDir

    private static final String PARENT = '''
        import grails.persistence.Entity

        @Entity
        abstract class Account {
            String name
            String secret
            boolean admin
            Date dateCreated

            static transients = ['secret']
            static constraints = {
                name bindable: true
                dateCreated bindable: true
                admin bindable: false
            }
        }
    '''

    private static final String CHILD = '''
        import grails.persistence.Entity

        @Entity
        class Customer extends Account {
            String email
        }
    '''

    private static final String CONTACT = '''
        class Contact {
            String name
            String phone
        }
    '''

    /** The base class of command objects is not an action parameter itself, so no controller gives it white lists. */
    private static final String COMMAND_PARENT = '''
        import grails.validation.Validateable

        abstract class BaseCommand implements Validateable {
            @Delegate Contact contact = new Contact()
            String note
        }
    '''

    private static final String COMMAND = '''
        class CustomerCommand extends BaseCommand {
            String email
        }
    '''

    /** A controller recognized by its annotation, as one outside grails-app is. */
    private static final String CONTROLLER = '''
        import grails.artefact.Artefact

        @Artefact('Controller')
        class CustomerController {
            def save(CustomerCommand command) { }
        }
    '''

    /** A controller as an application declares it, recognized by where it is rather than by an annotation. */
    private static final String PLAIN_CONTROLLER = '''
        class CustomerController {
            def save(CustomerCommand command) { }
        }
    '''

    /** A base with nothing to record: no constraints, no transients list, not a command object. */
    private static final String TRANSIENT_PARENT = '''
        abstract class Account {
            String name
            transient String token
            transient Contact helper
        }
    '''

    /** A base whose only rule a subclass inherits. */
    private static final String CONSTRAINED_PARENT = '''
        abstract class Account {
            String name
            boolean admin

            static constraints = {
                admin bindable: false
            }
        }
    '''

    /** A class that writes its white lists itself. */
    private static final String DECLARED_PARENT = '''
        abstract class Account {
            public static final List $defaultDatabindingWhiteList = ['name']
            public static final List $legacyDatabindingWhiteList = ['name']

            String name
            String secret
        }
    '''

    /** A delegate that keeps a property of its own out of binding. */
    private static final String DELEGATED_ADDRESS = '''
        class DelegatedAddress {
            String city
            boolean verified

            static constraints = {
                verified bindable: false
            }
        }
    '''

    private static final String DELEGATING_COMMAND = '''
        import grails.validation.Validateable

        class DelegatingCommand implements Validateable {
            String name
            @Delegate DelegatedAddress address = new DelegatedAddress()
        }
    '''

    /** A trait that gives a command object a property, and keeps a field to itself. */
    private static final String NAMED_TRAIT = '''
        trait Named {
            String name
            private String hidden
        }
    '''

    private static final String TRAIT_COMMAND = '''
        import grails.validation.Validateable

        class CustomerCommand implements Named, Validateable {
            String email
        }
    '''

    /** A command object as a plugin or a shared module compiles it: with no controller that binds it in sight. */
    private static String standaloneCommand(String declarations) {
        """
            import grails.validation.Validateable

            class CustomerCommand implements Validateable {
                String name
                String email
                String secret
                boolean admin
                transient String token
                ${declarations}
            }
        """
    }

    void 'a domain class compiled against its compiled parent gets the white lists it gets when compiled with it'() {
        given: 'the parent compiled on its own, as a module or a plugin is'
        File parentClasses = compile([Account: PARENT], new File(tempDir, 'parent'))

        and: 'the child compiled against those classes'
        File childClasses = compile([Customer: CHILD], new File(tempDir, 'child'), parentClasses)

        and: 'both compiled together'
        File togetherClasses = compile([Account: PARENT, Customer: CHILD], new File(tempDir, 'together'))

        when:
        Map<String, Set<String>> together = whiteLists(togetherClasses, 'Customer')
        Map<String, Set<String>> separate = whiteLists(childClasses, 'Customer', parentClasses)

        then: 'the declared and inherited properties are bindable, including one the parent makes bindable by constraint'
        together.legacy.containsAll(['name', 'email', 'dateCreated'])

        and: 'under deny by default, only the properties the parent constrains bindable: true are'
        together.denyByDefault == ['name', 'dateCreated'] as Set

        and: "the parent's transient, its non-bindable property and Groovy's metaClass are not"
        together.legacy.intersect(['secret', 'admin', 'metaClass']).empty
        separate.legacy.intersect(['secret', 'admin', 'metaClass']).empty

        and: 'the subclass gets the same white lists either way'
        separate == together

        and: 'binding through the public API preserves those rules'
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', admin: true,
                                    secret: 'submitted', dateCreated: new Date(123456L)]
        bindValues(childClasses, 'Customer', values, parentClasses) == values + [admin: false, secret: null]
    }

    void 'a command object compiled against its compiled parent binds what the parent delegates, as it does when compiled with it'() {
        given: 'the parent compiled on its own, as a module or a plugin is'
        File parentClasses = compile([Contact: CONTACT, BaseCommand: COMMAND_PARENT], new File(tempDir, 'parent'))

        and: 'the command object and its controller compiled against those classes'
        File commandClasses = new File(tempDir, 'command')
        String warnings = warningsOf {
            compile([CustomerCommand: COMMAND, CustomerController: CONTROLLER], commandClasses, parentClasses)
        }

        and: 'all compiled together, the parent before the controller that is recognized by its annotation'
        File togetherClasses = compile([Contact: CONTACT, BaseCommand: COMMAND_PARENT, CustomerCommand: COMMAND, CustomerController: CONTROLLER],
                new File(tempDir, 'together'))

        when:
        Map<String, Set<String>> together = whiteLists(togetherClasses, 'CustomerCommand')
        Map<String, Set<String>> separate = whiteLists(commandClasses, 'CustomerCommand', parentClasses)

        then: "the command object's own properties, its parent's, and those its parent delegates are bindable"
        separate.legacy.containsAll(['email', 'note', 'contact', 'name', 'phone'])

        and: "Groovy's metaClass is not"
        !separate.legacy.contains('metaClass')
        !separate.denyByDefault.contains('metaClass')

        and: 'the command object gets the same white lists either way'
        separate == together

        and: 'a parent compiled with its record is nothing to warn about'
        !warnings.contains('BaseCommand')

        and: 'the delegated setters remain usable through the public binding API'
        Map<String, Object> values = [name: 'Sample', phone: '123456', note: 'Note', email: 'sample@example.invalid']
        bindValues(commandClasses, 'CustomerCommand', values, parentClasses) == values
    }

    void 'a command object compiled as an application lays its sources out binds what its compiled parent delegates'() {
        given: 'the parent compiled on its own, as a module or a plugin is'
        File parentClasses = compile([Contact: CONTACT, BaseCommand: COMMAND_PARENT], new File(tempDir, 'parent'))

        and: 'the command object and its controller compiled against it, laid out as an application lays them out'
        Map<String, String> commandSources = ['src/main/groovy/CustomerCommand.groovy': COMMAND,
                                              'grails-app/controllers/CustomerController.groovy': PLAIN_CONTROLLER]
        File commandClasses = compileProject(commandSources, new File(tempDir, 'command'), parentClasses)

        and: 'all compiled together, as an application compiles its own sources'
        Map<String, String> parentSources = ['src/main/groovy/Contact.groovy': CONTACT, 'src/main/groovy/BaseCommand.groovy': COMMAND_PARENT]
        File togetherClasses = compileProject(parentSources + commandSources, new File(tempDir, 'together'))

        expect: 'the controller was recognized by where it is'
        isArtefact(commandClasses, 'CustomerController', parentClasses)
        isArtefact(togetherClasses, 'CustomerController')

        when:
        Set<String> separate = whiteLists(commandClasses, 'CustomerCommand', parentClasses).legacy
        Set<String> together = whiteLists(togetherClasses, 'CustomerCommand').legacy

        then: "compiled against the compiled parent, the command object binds the parent's properties and those it delegates"
        separate.containsAll(['email', 'note', 'contact', 'name', 'phone'])
        !separate.contains('metaClass')

        and: "compiled with the parent, its white lists are computed before the parent's @Delegate adds the setters, as it always was: the delegated properties are reached through the delegate"
        together.containsAll(['email', 'note', 'contact'])
        together.intersect(['name', 'phone']).empty
    }

    void 'a domain class compiled against a #kind parent without the record is warned about and still does not bind metaClass'() {
        given: 'the parent compiled as an earlier Grails compiled it, without a record of its names'
        File parentClasses = compileWithoutRecord([Account: PARENT.replace('@Entity', annotation)], new File(tempDir, 'parent'))

        when: 'the child is compiled against those classes'
        File childClasses = new File(tempDir, 'child')
        String warnings = warningsOf {
            compile([Customer: CHILD], childClasses, parentClasses)
        }
        Map<String, Set<String>> separate = whiteLists(childClasses, 'Customer', parentClasses)

        then: 'the compilation warns, naming the child and the parent'
        warnings.contains('white list of [Customer]')
        warnings.contains('bytecode of [Account]')

        and: 'the declared and inherited properties are bindable'
        separate.legacy.containsAll(['name', 'email'])

        and: "Groovy's metaClass is not"
        !separate.legacy.contains('metaClass')
        !separate.denyByDefault.contains('metaClass')

        where:
        kind     | annotation
        'domain' | '@Entity'
        'plain'  | ''
    }

    void 'the warning about a parent without the record names the class being compiled, also through an intermediate class without one'() {
        given: 'a parent and an intermediate class compiled as an earlier Grails compiled them, without records'
        File parentClasses = compileWithoutRecord([Account: PARENT], new File(tempDir, 'parent'))
        String intermediateSource = 'abstract class IntermediateAccount extends Account { String note }'
        File intermediateClasses = compileWithoutRecord([IntermediateAccount: intermediateSource], new File(tempDir, 'intermediate'), parentClasses)

        when: 'a domain class is compiled against both'
        File childClasses = new File(tempDir, 'child')
        String childSource = CHILD.replace('extends Account', 'extends IntermediateAccount')
        String warnings = warningsOf {
            compile([Customer: childSource], childClasses, intermediateClasses, parentClasses)
        }

        then: 'each warning names the class being compiled and the parent recomputed, not the intermediate class recomputed on the way'
        warnings.count('white list of [Customer]') == 2
        warnings.contains('bytecode of [IntermediateAccount]')
        warnings.contains('bytecode of [Account]')
        !warnings.contains('white list of [IntermediateAccount]')
    }

    void 'a domain class compiled against a parent Grails 7 recorded keeps what the parent makes bindable, with asmResolving=#asmResolving'() {
        given: 'the parent compiled as Grails 7 compiles it: its record names the one white list Grails 7 has, but no deny-by-default list'
        File parentClasses = compileAsGrails7([Account: PARENT], new File(tempDir, 'parent'))

        when: 'the child is compiled against those classes'
        File childClasses = new File(tempDir, 'child')
        String warnings = warningsOf {
            compile([Customer: CHILD], childClasses, [parentClasses], null, asmResolving)
        }
        Map<String, Set<String>> separate = whiteLists(childClasses, 'Customer', parentClasses)

        then: 'a parent with a record is nothing to warn about'
        !warnings.contains('Account')

        and: "the legacy list is the parent's, with the child's own property, and keeps the timestamp the parent makes bindable"
        separate.legacy == ['name', 'email', 'dateCreated'] as Set

        and: 'under deny by default, that timestamp is all the record tells of the parent'
        separate.denyByDefault == ['dateCreated'] as Set

        where:
        asmResolving << [true, false]
    }

    void 'a record that leaves a member out is read as recording nothing for it, not recomputed from bytecode'() {
        given: 'the parent compiled with a record that holds the names of both lists but not the explicitly bindable properties'
        File parentClasses = compileWithRecordEdited([Account: PARENT], new File(tempDir, 'parent'), { ClassNode classNode ->
            classNode.annotations.find { AnnotationNode annotation -> isRecord(annotation) }.members.remove('explicitlyBindable')
        })

        when: 'the child is compiled against it'
        File childClasses = new File(tempDir, 'child')
        String warnings = warningsOf {
            compile([Customer: CHILD], childClasses, parentClasses)
        }
        Map<String, Set<String>> separate = whiteLists(childClasses, 'Customer', parentClasses)

        then: 'nothing is recomputed, so nothing is warned about, and the recorded rules hold'
        !warnings.contains('white list of')
        separate.legacy.containsAll(['name', 'email'])
        separate.legacy.intersect(['secret', 'admin', 'metaClass']).empty
        separate.denyByDefault == ['name'] as Set

        and: 'the member left out recorded nothing: the timestamp the parent made bindable by constraint is not inherited'
        !separate.legacy.contains('dateCreated')
    }

    void 'a #kind base preserves its binding declarations with asmResolving=#asmResolving'() {
        given: 'a base that is neither a domain class nor an action parameter'
        String parentSource = PARENT.replace('@Entity', annotation)
                .replace('abstract class Account', "abstract class Account ${interfaces}")
        String childSource = commandObject ? CHILD.replace('@Entity', '') : CHILD
        Map<String, String> childSources = [Customer: childSource]
        if (commandObject) {
            childSources.CustomerController = CONTROLLER.replace('CustomerCommand', 'Customer')
        }
        File parentClasses = compile([Account: parentSource], new File(tempDir, 'parent'))
        File childClasses = compile(childSources, new File(tempDir, 'child'), [parentClasses], null, asmResolving)
        File togetherClasses = compile([Account: parentSource] + childSources, new File(tempDir, 'together'))

        when: 'the same input is bound to a subclass compiled separately and one compiled with its base'
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', admin: true,
                                    secret: 'submitted', dateCreated: new Date(123456L)]
        Map<String, Object> together = bindValues(togetherClasses, 'Customer', values)
        Map<String, Object> separate = bindValues(childClasses, 'Customer', values, parentClasses)

        then: 'the inherited restrictions and the explicitly bindable timestamp are preserved'
        together == values + [admin: false, secret: null]
        separate == together

        where:
        kind          | annotation                              | interfaces                                  | commandObject | asmResolving
        'plain'       | ''                                      | ''                                          | false         | true
        'plain'       | ''                                      | ''                                          | false         | false
        'dirty-check' | '@grails.gorm.dirty.checking.DirtyCheck'  | ''                                          | false         | true
        'dirty-check' | '@grails.gorm.dirty.checking.DirtyCheck'  | ''                                          | false         | false
        'command'     | ''                                      | 'implements grails.validation.Validateable' | true          | true
        'command'     | ''                                      | 'implements grails.validation.Validateable' | true          | false
    }

    void 'a #kind compiled against a base with only a transient property keeps it out of the white list, as when compiled with it'() {
        given: 'the base compiled on its own: with nothing to record, it gets no white lists, and a subclass reads its bytecode'
        File parentClasses = compile([Contact: CONTACT, Account: TRANSIENT_PARENT], new File(tempDir, 'parent'))

        and: 'a subclass compiled against those classes, and one compiled with them'
        Map<String, String> childSources = [Customer: childSource]
        if (controller) {
            childSources.CustomerController = CONTROLLER.replace('CustomerCommand', 'Customer')
        }
        File childClasses = compile(childSources, new File(tempDir, 'child'), parentClasses)
        File togetherClasses = compile([Contact: CONTACT, Account: TRANSIENT_PARENT] + childSources, new File(tempDir, 'together'))

        expect:
        !fieldNames(parentClasses, 'Account').contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)

        when:
        List<String> separate = legacyIncludeList(childClasses, 'Customer', parentClasses)
        List<String> together = legacyIncludeList(togetherClasses, 'Customer')

        then: 'neither the transient properties nor the patterns that would let a request reach into the typed one are in the list'
        !separate.any { String name -> name.startsWith('token') || name.startsWith('helper') }
        separate as Set == together as Set

        and: 'binding through the public API leaves them alone'
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', token: 'submitted']
        bindValues(childClasses, 'Customer', values, parentClasses) == values + [token: null]

        where:
        kind             | childSource                  | controller
        'domain class'   | CHILD                        | false
        'command object' | CHILD.replace('@Entity', '') | true
    }

    void 'a constrained compiled command base keeps delegated and explicitly bindable properties'() {
        given: 'a command base whose generated setters and declared setters participate in binding'
        String parentSource = COMMAND_PARENT.replace('String note', '''
            String note
            String secret
            String token
            transient Integer total

            static transients = ['secret', 'token', 'calculatedValue']
            static constraints = {
                phone bindable: false
                token bindable: true
            }

            void setCalculatedValue(Integer value) { total = value }
            Integer getCalculatedValue() { total }
        ''')
        File parentClasses = compile([Contact: CONTACT, BaseCommand: parentSource], new File(tempDir, 'parent'))
        File commandClasses = compile([CustomerCommand: COMMAND, CustomerController: CONTROLLER], new File(tempDir, 'command'), parentClasses)
        File togetherClasses = compile([Contact: CONTACT, BaseCommand: parentSource, CustomerCommand: COMMAND, CustomerController: CONTROLLER],
                new File(tempDir, 'together'))

        when:
        Map<String, Object> values = [name: 'Sample', phone: '123456', note: 'Note', email: 'sample@example.invalid',
                                    secret: 'submitted', token: 'allowed', calculatedValue: 42]
        Map<String, Object> separate = bindValues(commandClasses, 'CustomerCommand', values, parentClasses)
        Map<String, Object> together = bindValues(togetherClasses, 'CustomerCommand', values)

        then:
        together == values + [phone: null, secret: null]
        separate == together
    }

    void 'a compiled base with only #declaration preserves its rules through a plain intermediate class'() {
        given:
        String parentSource = """
            abstract class Account {
                String name
                String secret
                ${declaration}
            }
        """
        String intermediateSource = 'abstract class IntermediateAccount extends Account { String note }'
        String childSource = CHILD.replace('extends Account', 'extends IntermediateAccount')
        File parentClasses = compile([Account: parentSource], new File(tempDir, 'parent'))
        File intermediateClasses = compile([IntermediateAccount: intermediateSource], new File(tempDir, 'intermediate'), parentClasses)
        File childClasses = compile([Customer: childSource], new File(tempDir, 'child'), intermediateClasses, parentClasses)
        File togetherClasses = compile([Account: parentSource, IntermediateAccount: intermediateSource, Customer: childSource],
                new File(tempDir, 'together'))

        when:
        Map<String, Object> values = [name: 'Sample', note: 'Note', email: 'sample@example.invalid', secret: 'submitted']
        Map<String, Object> separate = bindValues(childClasses, 'Customer', values, intermediateClasses, parentClasses)
        Map<String, Object> together = bindValues(togetherClasses, 'Customer', values)

        then:
        together == values + [secret: null]
        separate == together

        where:
        declaration << ["static transients = ['secret']", 'static constraints = { secret bindable: false }']
    }

    void 'a class that only inherits its binding rules is bound by them, compiled #compiledWith its base'() {
        given: 'a base with a rule, and a subclass that declares nothing, with no controller that binds either in sight'
        String childSource = 'class Customer extends Account { String email }'
        File parentClasses = compile([Account: CONSTRAINED_PARENT], new File(tempDir, 'parent'))
        File childClasses = separately ? compile([Customer: childSource], new File(tempDir, 'child'), parentClasses)
                                       : compile([Account: CONSTRAINED_PARENT, Customer: childSource], new File(tempDir, 'child'))
        File[] classpath = separately ? [parentClasses] as File[] : [] as File[]

        expect: 'the subclass gets white lists of its own'
        fieldNames(childClasses, 'Customer', classpath).contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)
        fieldNames(childClasses, 'Customer', classpath).contains(DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST)

        and: "the base's rule holds when the subclass is bound"
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', admin: true]
        bindValues(childClasses, 'Customer', values, classpath) == values + [admin: false]

        where:
        compiledWith | separately
        'against'    | true
        'with'       | false
    }

    void 'a class that declares its white lists itself keeps them, and a subclass compiled with it inherits them'() {
        given: 'the parent compiled with a subclass that declares nothing'
        File classes = compile([Account: DECLARED_PARENT, Customer: CHILD.replace('@Entity', '')], new File(tempDir, 'declared'))

        expect: 'the parent keeps the lists it declares'
        whiteLists(classes, 'Account') == [legacy: ['name'] as Set, denyByDefault: ['name'] as Set]

        and: "the subclass is bound by the parent's lists and its own properties"
        whiteLists(classes, 'Customer') == [legacy: ['name', 'email'] as Set, denyByDefault: ['name'] as Set]
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', secret: 'submitted']
        bindValues(classes, 'Customer', values) == values + [secret: null]
    }

    void 'a class that declares its white lists itself gives a subclass compiled against it those lists'() {
        given: 'the class compiled on its own'
        File parentClasses = compile([Account: DECLARED_PARENT], new File(tempDir, 'parent'))

        when: 'a subclass that declares nothing is compiled against it'
        File childClasses = new File(tempDir, 'child')
        String warnings = warningsOf {
            compile([Customer: CHILD.replace('@Entity', '')], childClasses, parentClasses)
        }

        then: 'the lists the class declares are read from the compiled class, as generated lists are, and nothing is warned about'
        !warnings.contains('white list of')
        whiteLists(childClasses, 'Customer', parentClasses) == [legacy: ['name', 'email'] as Set, denyByDefault: ['name'] as Set]
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', secret: 'submitted']
        bindValues(childClasses, 'Customer', values, parentClasses) == values + [secret: null]
    }

    void 'a class that declares its white lists itself is recorded however it is compiled with a subclass, #how'() {
        given: 'the class compiled with a subclass'
        File parentClasses = compile(sources, new File(tempDir, 'parent'))

        when: 'another subclass is compiled against the result'
        File otherClasses = compile([Other: 'class Other extends Account { String phone }'], new File(tempDir, 'other'), parentClasses)

        then: 'it reads the lists the class declares'
        whiteLists(otherClasses, 'Other', parentClasses).legacy == ['name', 'phone'] as Set

        where:
        how                                            | sources
        'the class first'                              | [Account: DECLARED_PARENT, Customer: CHILD.replace('@Entity', '')]
        'the subclass first'                           | [Customer: CHILD.replace('@Entity', ''), Account: DECLARED_PARENT]
        'with a command object bound by a controller'  | [Account: DECLARED_PARENT, AccountCommand: 'class AccountCommand extends Account { String email }',
                                                          AccountController: CONTROLLER.replace('CustomerCommand', 'AccountCommand')]
    }

    void 'a subclass compiled without the transformation is bound by the white lists of its parent'() {
        given: 'a parent with rules, compiled with its white lists'
        File parentClasses = compile([Account: PARENT], new File(tempDir, 'parent'))

        and: 'a subclass compiled against it without the transformation, as a module without the data binding library compiles it'
        File childClasses = compileWithoutTransformation([Customer: CHILD.replace('@Entity', '')], new File(tempDir, 'child'), parentClasses)

        expect: 'the subclass got no white lists of its own'
        !fieldNames(childClasses, 'Customer', parentClasses).contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)

        and: "it is bound by its parent's list, which keeps the parent's rules and knows nothing of the subclass's own properties"
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', admin: true, secret: 'submitted']
        bindValues(childClasses, 'Customer', values, parentClasses) == values + [email: null, admin: false, secret: null]
    }

    void 'an action parameter compiled without white lists is warned about'() {
        given: 'a command object compiled without the transformation, as a plugin built without the data binding library compiles it'
        File commandClasses = compileWithoutTransformation([CustomerCommand: standaloneCommand('static constraints = { admin bindable: false }')],
                new File(tempDir, 'command'))

        when: 'a controller that binds it is compiled against it'
        File controllerClasses = new File(tempDir, 'controller')
        String warnings = warningsOf {
            compile([CustomerController: CONTROLLER], controllerClasses, commandClasses)
        }

        then: 'the compilation warns that the command object has no white list and should be recompiled'
        warnings.contains('[CustomerCommand] has binding rules but was compiled without a data binding white list')

        and: 'the command object is left as it was compiled'
        !fieldNames(commandClasses, 'CustomerCommand').contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)
    }

    void 'a property the delegate keeps out of binding stays out when reached through the delegating class, compiled #how'() {
        given: 'a command object that delegates to a class with a rule of its own, compiled with it or against it'
        File delegateClasses = compile([DelegatedAddress: DELEGATED_ADDRESS], new File(tempDir, 'delegate'))
        File commandClasses = together ? compile([DelegatedAddress: DELEGATED_ADDRESS, DelegatingCommand: DELEGATING_COMMAND], new File(tempDir, 'command'))
                                       : compile([DelegatingCommand: DELEGATING_COMMAND], new File(tempDir, 'command'), delegateClasses)
        File[] classpath = together ? [] as File[] : [delegateClasses] as File[]

        when:
        Set<String> whiteList = whiteLists(commandClasses, 'DelegatingCommand', classpath).legacy
        Object target = bind(commandClasses, 'DelegatingCommand', [city: 'Lund', verified: true], classpath)

        then: "the delegate's property is bindable through the delegating class, and what the delegate keeps out is not"
        whiteList.containsAll(['name', 'address', 'city'])
        !whiteList.contains('verified')
        target.address.city == 'Lund'
        !target.address.verified

        where:
        how                    | together
        'with the delegate'    | true
        'against the delegate' | false
    }

    void 'a compiled base that delegates keeps what the delegate keeps out of binding out of a subclass compiled against it'() {
        given: 'a plain base that delegates, compiled on its own: with no rules of its own, a subclass reads its bytecode'
        String baseSource = '''
            abstract class DelegatingBase {
                @Delegate DelegatedAddress address = new DelegatedAddress()
                String note
            }
        '''
        File baseClasses = compile([DelegatedAddress: DELEGATED_ADDRESS, DelegatingBase: baseSource], new File(tempDir, 'base'))

        and: 'a command object extending it, compiled against those classes and compiled with them'
        Map<String, String> childSources = [Customer: 'class Customer extends DelegatingBase { String email }',
                                            CustomerController: CONTROLLER.replace('CustomerCommand', 'Customer')]
        File childClasses = compile(childSources, new File(tempDir, 'child'), baseClasses)
        File togetherClasses = compile([DelegatedAddress: DELEGATED_ADDRESS, DelegatingBase: baseSource] + childSources, new File(tempDir, 'together'))

        expect: 'the base got no white lists of its own'
        !fieldNames(baseClasses, 'DelegatingBase').contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)

        when:
        Set<String> separate = whiteLists(childClasses, 'Customer', baseClasses).legacy
        Set<String> together = whiteLists(togetherClasses, 'Customer').legacy

        then: "the delegate's property is bindable, what the delegate keeps out is not, and the subclass gets the same list either way"
        separate.containsAll(['note', 'address', 'city', 'email'])
        !separate.contains('verified')
        separate == together
    }

    void 'a setter written by hand for a transient field binds, from bytecode as when compiled together'() {
        given: 'a plain base with a transient field and a setter of its own for it'
        String baseSource = '''
            abstract class Account {
                String name
                transient String token

                void setToken(String value) { token = value }
            }
        '''
        Map<String, String> childSources = [Customer: CHILD.replace('@Entity', ''), CustomerController: CONTROLLER.replace('CustomerCommand', 'Customer')]
        File parentClasses = compile([Account: baseSource], new File(tempDir, 'parent'))
        File childClasses = compile(childSources, new File(tempDir, 'child'), parentClasses)
        File togetherClasses = compile([Account: baseSource] + childSources, new File(tempDir, 'together'))

        expect: 'the setter the base declares binds, whichever way the subclass is compiled'
        whiteLists(togetherClasses, 'Customer').legacy.contains('token')
        whiteLists(childClasses, 'Customer', parentClasses) == whiteLists(togetherClasses, 'Customer')
    }

    void 'a property named like a binding rule is not one'() {
        given: 'a class with instance properties named constraints and transients, and no rules'
        File classes = compile([Report: '''
            class Report {
                String name
                Map constraints = [:]
                List transients = []
            }
        '''], new File(tempDir, 'report'))

        expect: 'it gets no white lists, and is bound as it always was'
        !fieldNames(classes, 'Report').contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)
        !fieldNames(classes, 'Report').contains(DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST)
        bindValues(classes, 'Report', [name: 'Sample']) == [name: 'Sample']
    }

    void 'a command object nested in another class is bound by the rules it declares'() {
        given: 'a command object declared inside another class, as a controller may hold one'
        File classes = compile([Forms: '''
            import grails.validation.Validateable

            class Forms {
                static class SignUp implements Validateable {
                    String name
                    boolean admin

                    static constraints = {
                        admin bindable: false
                    }
                }
            }
        '''], new File(tempDir, 'forms'))

        expect:
        fieldNames(classes, 'Forms$SignUp').contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)
        bindValues(classes, 'Forms$SignUp', [name: 'Sample', admin: true]) == [name: 'Sample', admin: false]
    }

    void 'a property whose type has a supertype off the compile classpath compiles and binds'() {
        given: 'a type that implements an interface, compiled on its own'
        File typeClasses = compile([Marker: 'interface Marker { }', Special: 'class Special implements Marker { String code }'],
                new File(tempDir, 'types'))

        and: 'only the type, not its interface, on the compile classpath of a command object with a property of that type'
        File partialClasses = new File(tempDir, 'partial')
        partialClasses.mkdirs()
        new File(partialClasses, 'Special.class').bytes = new File(typeClasses, 'Special.class').bytes

        when:
        File commandClasses = compile([CustomerCommand: '''
            import grails.validation.Validateable

            class CustomerCommand implements Validateable {
                String name
                Special special
            }
        '''], new File(tempDir, 'command'), partialClasses)

        then: 'the compilation succeeds, and the property is bindable like any other'
        whiteLists(commandClasses, 'CustomerCommand', typeClasses).legacy.containsAll(['name', 'special'])
        bindValues(commandClasses, 'CustomerCommand', [name: 'Sample'], typeClasses) == [name: 'Sample']
    }

    void 'a command object compiled apart from its controller #description is bound by the rules it declares'() {
        given: 'the command object compiled on its own, as a plugin or a shared module compiles it'
        File commandClasses = compile([CustomerCommand: standaloneCommand(declarations)], new File(tempDir, 'command'))

        and: 'compiled with the controller that binds it, as an application compiles its own'
        File togetherClasses = compile([CustomerCommand: standaloneCommand(declarations), CustomerController: CONTROLLER],
                new File(tempDir, 'together'))

        when: 'the same input is bound to each through the public binding API'
        Map<String, Object> values = [name: 'Sample', email: 'sample@example.invalid', secret: 'submitted', admin: true, token: 'submitted']
        Map<String, Object> separate = bindValues(commandClasses, 'CustomerCommand', values)
        Map<String, Object> together = bindValues(togetherClasses, 'CustomerCommand', values)

        then: 'the command object gets the same white list fields whether or not a controller binds it'
        fieldNames(commandClasses, 'CustomerCommand').contains(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST)
        fieldNames(commandClasses, 'CustomerCommand').contains(DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST)
        whiteLists(commandClasses, 'CustomerCommand') == whiteLists(togetherClasses, 'CustomerCommand')

        and: 'what the command object declares transient or non-bindable is not bound either way'
        together == values + unbound
        separate == together

        where:
        description                       | declarations                                                                      | unbound
        'with constraints and transients' | "static transients = ['secret']\n static constraints = { admin bindable: false }" | [secret: null, admin: false, token: null]
        'with a transient field alone'    | ''                                                                                | [token: null]
    }

    void 'a command object compiled with #compiledWith binds what it and its traits declare, not what its traits keep to themselves'() {
        given: 'a command object with a trait, compiled with the controller that binds it or on its own'
        Map<String, String> sources = [Named: NAMED_TRAIT, CustomerCommand: TRAIT_COMMAND]
        if (controller) {
            sources.CustomerController = CONTROLLER
        }
        File classes = compile(sources, new File(tempDir, 'command'))

        expect: "the white list names its property and the trait's, and nothing of the traits' own"
        whiteLists(classes, 'CustomerCommand').legacy == ['email', 'name'] as Set

        when: "input names the traits' own fields and the validation result along with the properties"
        Object target = bind(classes, 'CustomerCommand', [name: 'Sample', email: 'sample@example.invalid', Named__hidden: 'submitted',
                                                          grails_validation_Validateable__errors: 'submitted', errors: [nestedPath: 'submitted.']])

        then: "the command object's property and the trait's are bound"
        target.name == 'Sample'
        target.email == 'sample@example.invalid'

        and: "the trait's own field and the validation result are left alone"
        target.Named__hidden == null
        target.errors.nestedPath == ''
        !target.errors.hasErrors()

        where:
        compiledWith     | controller
        'its controller' | true
        'nothing else'   | false
    }

    private File compile(Map<String, String> sources, File target, File... classpath) {
        compile(sources, target, classpath.toList(), null)
    }

    /**
     * Compiles as a Grails before the record did: the class gets its generated white lists, but not the record of
     * the names in them.
     */
    private File compileWithoutRecord(Map<String, String> sources, File target, File... classpath) {
        compileWithRecordEdited(sources, target, { ClassNode classNode ->
            classNode.annotations.removeIf { AnnotationNode annotation -> isRecord(annotation) }
        }, classpath)
    }

    /**
     * Compiles as Grails 7 does: the record names the one white list Grails 7 has and the explicitly bindable
     * special properties, but no deny-by-default list.
     */
    private File compileAsGrails7(Map<String, String> sources, File target, File... classpath) {
        compileWithRecordEdited(sources, target, { ClassNode classNode ->
            classNode.annotations.findAll { AnnotationNode annotation -> isRecord(annotation) }*.members*.remove('denyByDefault')
        }, classpath)
    }

    /** Compiles, then edits the record of each class as another Grails might have written it. */
    private File compileWithRecordEdited(Map<String, String> sources, File target, Closure<?> edit, File... classpath) {
        compile(sources, target, classpath.toList(), edit)
    }

    /** Compiles as a module without the data binding library does: no class gets white lists from the transformation. */
    private File compileWithoutTransformation(Map<String, String> sources, File target, File... classpath) {
        compile(sources, target, classpath.toList(), null, true, false)
    }

    private File compile(Map<String, String> sources, File target, List<File> classpath, Closure<?> recordEdit, boolean asmResolving = true,
                         boolean withTransformation = true) {
        target.mkdirs()
        CompilerConfiguration configuration = configuration(target, classpath, asmResolving)
        if (!withTransformation) {
            configuration.disabledGlobalASTTransformations = [DatabindingWhiteListASTTransformation.name] as Set
        }
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader, configuration)
        CompilationUnit unit = new CompilationUnit(configuration, null, loader)
        if (recordEdit != null) {
            unit.addPhaseOperation({ SourceUnit source, GeneratorContext context, ClassNode classNode ->
                recordEdit(classNode)
            } as CompilationUnit.IPrimaryClassNodeOperation, Phases.INSTRUCTION_SELECTION)
        }
        sources.each { String name, String text -> unit.addSource("${name}.groovy", text) }
        unit.compile()
        target
    }

    /** Compiles sources laid out as an application's, so that Grails recognizes its artefacts by where they are, as in a build. */
    private File compileProject(Map<String, String> sources, File projectDir, File... classpath) {
        File target = new File(projectDir, 'build/classes')
        target.mkdirs()
        CompilerConfiguration configuration = configuration(target, classpath.toList(), true)
        GroovyClassLoader loader = new GroovyClassLoader(getClass().classLoader, configuration)
        CompilationUnit unit = new CompilationUnit(configuration, null, loader)
        sources.each { String path, String text ->
            File file = new File(projectDir, path)
            file.parentFile.mkdirs()
            file.text = text
            unit.addSource(file)
        }
        unit.compile()
        target
    }

    private static CompilerConfiguration configuration(File target, List<File> classpath, boolean asmResolving) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = target
        configuration.classpathList = classpath*.absolutePath
        configuration.optimizationOptions.put('asmResolving', asmResolving)
        configuration
    }

    private static boolean isRecord(AnnotationNode annotation) {
        annotation.classNode.name == GeneratedDatabindingWhiteList.name
    }

    /** What a compilation warns about; Grails prints its compiler warnings to the standard error stream. */
    private static String warningsOf(Closure<?> compilation) {
        PrintStream original = System.err
        ByteArrayOutputStream captured = new ByteArrayOutputStream()
        System.err = new PrintStream(captured, true, StandardCharsets.UTF_8)
        try {
            compilation()
        } finally {
            System.err = original
        }
        captured.toString(StandardCharsets.UTF_8)
    }

    private <T> T withClasses(File classes, File[] more, Closure<T> work) {
        URL[] urls = ([classes] + more.toList())*.toURI()*.toURL() as URL[]
        new URLClassLoader(urls, getClass().classLoader).withCloseable(work)
    }

    /** The property names in both generated white lists, without the patterns. */
    private Map<String, Set<String>> whiteLists(File classes, String className, File... more) {
        withClasses(classes, more) { ClassLoader loader ->
            Class<?> type = loader.loadClass(className)
            [legacy       : names(type, DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST),
             denyByDefault: names(type, DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST)]
        }
    }

    private static Set<String> names(Class<?> type, String field) {
        List names = type.getField(field).get(null)
        names.findAll { String name -> !name.endsWith('_*') && !name.endsWith('.*') } as Set
    }

    /** The generated legacy white list as the binder reads it, patterns included. */
    private List<String> legacyIncludeList(File classes, String className, File... more) {
        withClasses(classes, more) { ClassLoader loader ->
            loader.loadClass(className).getField(DefaultASTDatabindingHelper.LEGACY_DATABINDING_WHITELIST).get(null) as List<String>
        }
    }

    private List<String> fieldNames(File classes, String className, File... more) {
        withClasses(classes, more) { ClassLoader loader -> loader.loadClass(className).declaredFields*.name }
    }

    private boolean isArtefact(File classes, String className, File... more) {
        withClasses(classes, more) { ClassLoader loader -> loader.loadClass(className).isAnnotationPresent(Artefact) }
    }

    private Object bind(File classes, String className, Map<String, Object> values, File... more) {
        withClasses(classes, more) { ClassLoader loader ->
            Object target = loader.loadClass(className).getDeclaredConstructor().newInstance()
            def result = DataBindingUtils.bindObjectToInstance(target, values)
            assert result == null || !result.hasErrors()
            target
        }
    }

    private Map<String, Object> bindValues(File classes, String className, Map<String, Object> values, File... more) {
        withClasses(classes, more) { ClassLoader loader ->
            Object target = loader.loadClass(className).getDeclaredConstructor().newInstance()
            def result = DataBindingUtils.bindObjectToInstance(target, values)
            assert result == null || !result.hasErrors()
            values.collectEntries { String name, Object value -> [name, target[name]] }
        }
    }
}
