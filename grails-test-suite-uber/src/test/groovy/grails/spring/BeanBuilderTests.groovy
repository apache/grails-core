/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package grails.spring

import grails.util.Holders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.SpringProxy
import org.springframework.beans.factory.BeanIsAbstractException
import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.config.Scope
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Component

import static org.junit.jupiter.api.Assertions.*

/**
 * @author Graeme Rocher
 * @since 0.4
 */
class BeanBuilderTests {

    private BeanBuilder bb = new BeanBuilder()

    @BeforeEach
    protected void setUp() {
        Holders.setPluginManager null
    }

    @Test
    void testImportSpringXml() {

        bb.beans {
            importBeans "classpath:grails/spring/test.xml"
        }

        def ctx = bb.createApplicationContext()

        def foo = ctx.getBean("foo")
        assertEquals "hello", foo
    }

    @Test
    void testImportBeansFromGroovy() {

        bb.beans {
            importBeans "file:test/resources/spring/test.groovy"
        }

        def ctx = bb.createApplicationContext()

        def foo = ctx.getBean("foo")
        assertEquals "hello", foo
    }

    @Test
    void testInheritPropertiesFromAbstractBean() {

        bb.beans {
            myB(Bean1) {
                person = "wombat"
            }

            myAbstractA(Bean2) { bean ->
                bean.'abstract' = true
                age = 10
                bean1 = myB
            }
            myConcreteB {
                it.parent = myAbstractA
            }
        }

        def ctx = bb.createApplicationContext()
        def bean = ctx.getBean("myConcreteB")

        assertEquals(10, (Integer) bean.age)
        assertNotNull bean.bean1
    }

//    void testContextComponentScanSpringTag() {
//
//        bb.beans {
//            xmlns grailsContext:"http://grails.org/schema/context"
//
//            grailsContext.'component-scan'('base-package' :"**")
//        }
//
//        def appCtx = bb.createApplicationContext()
//
//        def p = appCtx.getBean("person")
//
//        assertTrue(p instanceof AdvisedPerson)
//        assertNotNull p
//    }

    @Test
    void testUseSpringNamespaceAsMethod() {

        bb.beans {
            xmlns aop:"http://www.springframework.org/schema/aop"

            fred(AdvisedPerson) {
                name = "Fred"
                age = 45
            }
            birthdayCardSenderAspect(BirthdayCardSender)

            aop {
                config("proxy-target-class":true) {
                    aspect(id:"sendBirthdayCard",ref:"birthdayCardSenderAspect") {
                        after method:"onBirthday", pointcut: "execution(void grails.spring.AdvisedPerson.birthday()) and this(person)"
                    }
                }
            }
        }

        def appCtx = bb.createApplicationContext()
        def fred = appCtx.getBean("fred")
        assertTrue(fred instanceof SpringProxy)

        fred.birthday()

        BirthdayCardSender birthDaySender = appCtx.getBean("birthdayCardSenderAspect")

        assertEquals 1, birthDaySender.peopleSentCards.size()
        assertEquals "Fred", birthDaySender.peopleSentCards[0].name
    }

    @Test
    void testUseTwoSpringNamespaces() {

            TestScope scope = new TestScope()

            GenericApplicationContext appCtx = bb.getSpringConfig().getUnrefreshedApplicationContext()
            appCtx.getBeanFactory().registerScope("test", scope)
            bb.beans {
                xmlns aop:"http://www.springframework.org/schema/aop"
                xmlns util:"http://www.springframework.org/schema/util"
                scopedList(ArrayList) { bean ->
                    bean.scope = "test"
                    aop.'scoped-proxy'()
                }

                util.list(id:"foo") {
                    value "one"
                    value "two"
                }

            }

            appCtx = bb.createApplicationContext()

            assert ['one', 'two'] == appCtx.getBean("foo")

            assertNotNull appCtx.getBean("scopedList")
            assertNotNull appCtx.getBean("scopedList").size()
            assertNotNull appCtx.getBean("scopedList").size()

            // should only be true because bean not initialized until proxy called
            assertEquals 2, scope.instanceCount

            bb = new BeanBuilder()

            appCtx = bb.getSpringConfig().getUnrefreshedApplicationContext()
            appCtx.getBeanFactory().registerScope("test", scope)
            bb.beans {
                xmlns aop:"http://www.springframework.org/schema/aop",
                      util:"http://www.springframework.org/schema/util"
                scopedList(ArrayList) { bean ->
                    bean.scope = "test"
                    aop.'scoped-proxy'()
                }

                util.list(id:"foo") {
                    value "one"
                    value "two"
                }

            }
            appCtx = bb.createApplicationContext()

            assert ['one', 'two'] == appCtx.getBean("foo")

            assertNotNull appCtx.getBean("scopedList")
            assertNotNull appCtx.getBean("scopedList").size()
            assertNotNull appCtx.getBean("scopedList").size()

            // should only be true because bean not initialized until proxy called
            assertEquals 4, scope.instanceCount
    }

    @Test
    void testSpringAOPSupport() {

        bb.beans {
            xmlns aop:"http://www.springframework.org/schema/aop"

            fred(AdvisedPerson) {
                name = "Fred"
                age = 45
            }
            birthdayCardSenderAspect(BirthdayCardSender)

            aop.config("proxy-target-class":true) {
                aspect(id:"sendBirthdayCard",ref:"birthdayCardSenderAspect") {
                    after method:"onBirthday", pointcut: "execution(void grails.spring.AdvisedPerson.birthday()) and this(person)"
                }
            }
        }

        def appCtx = bb.createApplicationContext()
        def fred = appCtx.getBean("fred")
        assertTrue (fred instanceof SpringProxy)

        fred.birthday()

        BirthdayCardSender birthDaySender = appCtx.getBean("birthdayCardSenderAspect")

        assertEquals 1, birthDaySender.peopleSentCards.size()
        assertEquals "Fred", birthDaySender.peopleSentCards[0].name
    }

    @Test
    void testSpringScopedProxyBean() {

        GenericApplicationContext appCtx = bb.getSpringConfig().getUnrefreshedApplicationContext()
        TestScope scope = new TestScope()
        appCtx.getBeanFactory().registerScope("test", scope)
        bb.beans {
            xmlns aop:"http://www.springframework.org/schema/aop"
            scopedList(ArrayList) { bean ->
                bean.scope = "test"
                aop.'scoped-proxy'()
            }
        }

        appCtx = bb.createApplicationContext()

        assertNotNull appCtx.getBean("scopedList")
        assertNotNull appCtx.getBean("scopedList").size()
        assertNotNull appCtx.getBean("scopedList").size()

        // should only be true because bean not initialized until proxy called
        assertEquals 2, scope.instanceCount
    }

    @Test
    void testSpringNamespaceBean() {
        bb.beans {
            xmlns util:"http://www.springframework.org/schema/util"
            util.list(id:"foo") {
                value "one"
                value "two"
            }

        }

        ApplicationContext appCtx = bb.createApplicationContext()

        assert ['one', 'two'] == appCtx.getBean("foo")
    }

    @Test
    void testNamedArgumentConstructor() {
        bb.beans {
            holyGrail(HolyGrailQuest)
            knights(KnightOfTheRoundTable, "Camelot", leader:"lancelot", quest: holyGrail)
        }

        def ctx = bb.createApplicationContext()

        KnightOfTheRoundTable knights = ctx.getBean("knights")
        HolyGrailQuest quest = ctx.getBean("holyGrail")

        assertEquals "Camelot", knights.name
        assertEquals "lancelot", knights.leader
        assertEquals quest, knights.quest
    }

    @Test
    void testAbstractBeanDefinition() {
        bb.beans {
            abstractBean {
                leader = "Lancelot"
            }
            quest(HolyGrailQuest)
            knights(KnightOfTheRoundTable, "Camelot") { bean ->
                bean.parent = abstractBean
                quest = quest
            }
        }
        def ctx = bb.createApplicationContext()

        def knights = ctx.knights
        assert knights
        assertThrows(org.springframework.beans.factory.BeanIsAbstractException) {
            ctx.abstractBean
        }
        assertEquals "Lancelot", knights.leader
    }

    @Test
    void testAbstractBeanDefinitionWithClass() {
        bb.beans {
            abstractBean(KnightOfTheRoundTable) { bean ->
                bean.'abstract' = true
                leader = "Lancelot"
            }
            quest(HolyGrailQuest)
            knights("Camelot") { bean ->
                bean.parent = abstractBean
                quest = quest
            }
        }
        def ctx = bb.createApplicationContext()

        assertThrows(BeanIsAbstractException) {
            ctx.abstractBean
        }
        def knights = ctx.knights
        assert knights
        assertEquals "Lancelot", knights.leader
    }

    @Test
    void testScopes() {
        bb.beans {
            myBean(ScopeTest) { bean ->
                bean.scope = "prototype"
            }
            myBean2(ScopeTest)
        }
        def ctx = bb.createApplicationContext()

        def b1 = ctx.myBean
        def b2 = ctx.myBean

        assert b1 != b2

        b1 = ctx.myBean2
        b2 = ctx.myBean2

        assertEquals b1, b2
    }

    @Test
    void testSimpleBean() {
        bb.beans {
            bean1(Bean1) {
                person = "homer"
                age = 45
                props = [overweight:true, height:"1.8m"]
                children = ["bart", "lisa"]
            }
        }
        def ctx  = bb.createApplicationContext()

        assert ctx.containsBean("bean1")
        def bean1 = ctx.getBean("bean1")

        assertEquals "homer", bean1.person
        assertEquals 45, bean1.age
        assertEquals true, bean1.props?.overweight
        assertEquals "1.8m", bean1.props?.height
        assertEquals(["bart", "lisa"], bean1.children)
    }

    @Test
    void testBeanWithParentRef() {
        bb.beans {
            homer(Bean1) {
                person = "homer"
                age = 45
                props = [overweight:true, height:"1.8m"]
                children = ["bart", "lisa"]
            }
        }
        bb = new BeanBuilder(bb.createApplicationContext())
        bb.beans {
            bart(Bean2) {
                person = "bart"
                parent = ref("homer", true)
            }
        }

        def ctx = bb.createApplicationContext()
        assert ctx != null
        assert ctx.containsBean("bart")
        def bart = ctx.getBean("bart")
        assertEquals "homer",bart.parent?.person
    }

    @Test
    void testWithAnonymousInnerBean() {
        bb.beans {
            bart(Bean1) {
                person = "bart"
                age = 11
            }
            lisa(Bean1) {
                person = "lisa"
                age = 9
            }
            marge(Bean2) {
                person = "marge"
                bean1 = { Bean1 b ->
                    person = "homer"
                    age = 45
                    props = [overweight:true, height:"1.8m"]
                    children = ["bart", "lisa"]
                }
                children = [bart, lisa]
            }
        }

        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assertEquals "homer", marge.bean1.person
    }

    @Test
    void testAnonymousInnerBeanViaBeanMethod() {
        bb.beans {
            bart(Bean1) {
                person = "bart"
                age = 11
            }
            lisa(Bean1) {
                person = "lisa"
                age = 9
            }
            marge(Bean2) {
                person = "marge"
                bean1 =  bean(Bean1) {
                    person = "homer"
                    age = 45
                    props = [overweight:true, height:"1.8m"]
                    children = ["bart", "lisa"]
                }
                children = [bart, lisa]
            }
        }

        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assertEquals "homer", marge.bean1.person
    }

    @Test
    void testAnonymousInnerBeanViaBeanMethodWithConstructorArgs() {
        bb.beans {
            bart(Bean1) {
                person = "bart"
                age = 11
            }
            lisa(Bean1) {
                person = "lisa"
                age = 9
            }
            marge(Bean2) {
                person = "marge"
                bean3 =  bean(Bean3, "homer", lisa) {
                    person = "homer"
                    age = 45
                }
                children = [bart, lisa]
            }
        }

        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assertEquals "homer", marge.bean3.person
        assertEquals "lisa", marge.bean3.bean1.person
    }

    @Test
    void testWithUntypedAnonymousInnerBean() {
        bb.beans {
            homer(Bean1Factory)
            bart(Bean1) {
                person = "bart"
                age = 11
            }
            lisa(Bean1) {
                person = "lisa"
                age = 9
            }
            marge(Bean2) {
                person = "marge"
                bean1 = { bean ->
                    bean.factoryBean = "homer"
                    bean.factoryMethod = "newInstance"
                    person = "homer"
                }
                children = [bart, lisa]
            }
        }

        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assertEquals "homer", marge.bean1.person
    }

    @Test
    void testBeanReferences() {
        bb.beans {
            homer(Bean1) {
                person = "homer"
                age = 45
                props = [overweight:true, height:"1.8m"]
                children = ["bart", "lisa"]
            }
            bart(Bean1) {
                person = "bart"
                age = 11
            }
            lisa(Bean1) {
                person = "lisa"
                age = 9
            }
            marge(Bean2) {
                person = "marge"
                bean1 = homer
                children = [bart, lisa]
            }
        }
        def ctx  = bb.createApplicationContext()

        def homer = ctx.getBean("homer")
        def marge = ctx.getBean("marge")
        def bart = ctx.getBean("bart")
        def lisa = ctx.getBean("lisa")

        assertEquals homer, marge.bean1
        assertEquals 2, marge.children.size()

        assertTrue marge.children.contains(bart)
        assertTrue marge.children.contains(lisa)
    }

    @Test
    void testBeanWithConstructor() {
        bb.beans {
            homer(Bean1) {
                person = "homer"
                age = 45
            }
            marge(Bean3, "marge", homer) {
                age = 40
            }
        }
        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assertEquals "marge", marge.person
        assertEquals "homer", marge.bean1.person
        assertEquals 40, marge.age
    }

    @Test
    void testBeanWithListAndMapConstructor() {
        bb.beans {
            bart(Bean1) {
                person = "bart"
                age = 11
            }
            lisa(Bean1) {
                person = "lisa"
                age = 9
            }

            beanWithList(Bean5, [bart, lisa])

            // test runtime references both as ref() and as plain name
            beanWithMap(Bean6, [bart:bart, lisa:ref('lisa')])
        }
        def ctx  = bb.createApplicationContext()

        def beanWithList = ctx.getBean("beanWithList")
        assertEquals 2, (int) beanWithList.people.size()
        assertEquals "bart", beanWithList.people[0].person

        def beanWithMap = ctx.getBean("beanWithMap")
        assertEquals 9, beanWithMap.peopleByName.lisa.age
        assertEquals "bart", beanWithMap.peopleByName.bart.person
    }

    @Test
    void testBeanWithFactoryMethod() {
        bb.beans {
            homer(Bean1) {
                person = "homer"
                age = 45
            }
            def marge = marge(Bean4) {
                person = "marge"
            }
            marge.factoryMethod = "getInstance"
        }
        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assert "marge", marge.person
    }

    @Test
    void testBeanWithFactoryMethodUsingClosureArgs() {
        bb.beans {
            homer(Bean1) {
                person = "homer"
                age = 45
            }
            marge(Bean4) { bean ->
                bean.factoryMethod = "getInstance"
                person = "marge"
            }
        }
        def ctx  = bb.createApplicationContext()

        def marge = ctx.getBean("marge")

        assert "marge", marge.person
    }

    @Test
    void testBeanWithFactoryMethodWithConstructorArgs() {
        bb.beans {
            beanFactory(Bean1FactoryWithArgs) {}

            homer(beanFactory:"newInstance", "homer") {
                age = 45
            }
            //Test with no closure body
            marge(beanFactory:"newInstance", "marge")

            //Test more verbose method
            mcBain("mcBain") {
                bean ->
                bean.factoryBean="beanFactory"
                bean.factoryMethod="newInstance"

            }
        }
        def ctx  = bb.createApplicationContext()

        def homer = ctx.getBean("homer")

        assert "homer", homer.person
        assert 45, homer.age

        assert "marge", ctx.getBean("marge").person

        assert "mcBain", ctx.getBean("mcBain").person
    }

    @Test
    void testGetBeanDefinitions() {
        bb.beans {
            jeff(Bean1) {
                person = 'jeff'
            }
            graeme(Bean1) {
                person = 'graeme'
            }
            guillaume(Bean1) {
                person = 'guillaume'
            }
        }

        def beanDefinitions = bb.beanDefinitions
        assertNotNull beanDefinitions, 'beanDefinitions was null'
        assertEquals 3, beanDefinitions.size(), 'beanDefinitions was the wrong size'
        assertNotNull beanDefinitions['jeff'], 'beanDefinitions did not contain jeff'
        assertNotNull beanDefinitions['guillaume'], 'beanDefinitions did not contain guillaume'
        assertNotNull beanDefinitions['graeme'], 'beanDefinitions did not contain graeme'
    }

    @Test
    void testBeanWithFactoryBean() {
        bb.beans {
            myFactory(Bean1Factory)

            homer(myFactory) { bean ->
                bean.factoryMethod = "newInstance"
                person = "homer"
                age = 45
            }
        }

        def ctx  = bb.createApplicationContext()

        def homer = ctx.getBean("homer")

        assertEquals "homer", homer.person
    }

    @Test
    void testBeanWithFactoryBeanAndMethod() {
        bb.beans {
            myFactory(Bean1Factory)

            homer(myFactory:"newInstance") { bean ->
                person = "homer"
                age = 45
            }
        }

        def ctx  = bb.createApplicationContext()

        def homer = ctx.getBean("homer")

        assertEquals "homer", homer.person
    }

    @Test
    void testLoadExternalBeans() {
        def pr = new PathMatchingResourcePatternResolver()
        def r = pr.getResource("grails/spring/resources1.groovy")

        bb.loadBeans(r)

        def ctx = bb.createApplicationContext()

        assert ctx.containsBean("dataSource")

        def dataSource = ctx.getBean("dataSource")
    }

    @Test
    void testHolyGrailWiring() {

        bb.beans {
            quest(HolyGrailQuest)

            knight(KnightOfTheRoundTable, "Bedivere") {
                quest = ref("quest")
            }
        }

        def ctx = bb.createApplicationContext()

        def knight = ctx.getBean("knight")

        knight.embarkOnQuest()
    }

    @Test
    void testAbstractBeanSpecifyingClass() {

        bb.beans {
            abstractKnight(KnightOfTheRoundTable) { bean ->
                bean.'abstract' = true
                leader = "King Arthur"
            }

            lancelot("lancelot") { bean ->
                bean.parent = ref("abstractKnight")
            }

            abstractPerson(Bean1) { bean ->
                bean.'abstract'=true
                age = 45
            }
            homerBean { bean ->
                bean.parent = ref("abstractPerson")
                person = "homer"
            }
        }

        def ctx = bb.createApplicationContext()

        def lancelot = ctx.getBean("lancelot")
        assertEquals "King Arthur", lancelot.leader
        assertEquals "lancelot", lancelot.name

        def homerBean = ctx.getBean("homerBean")

        assertEquals 45, ((Integer) homerBean.age)
        assertEquals "homer", homerBean.person
    }

    @Test
    void testBeanBuilderWithScript() {
        def script = '''
def bb = new grails.spring.BeanBuilder()

bb.beans {
quest(grails.spring.HolyGrailQuest) {}

knight(grails.spring.KnightOfTheRoundTable, "Bedivere") { quest = quest }
}
bb.createApplicationContext()
'''
        def ctx = new GroovyShell().evaluate(script)

        def knight = ctx.getBean('knight')
        knight.embarkOnQuest()
    }

    @Test
    // test for GRAILS-5057
    void testRegisterBeans() {

        bb.beans {
           personA(AdvisedPerson) {
               name = "Bob"
           }
        }

        def appCtx = bb.createApplicationContext()

        assertEquals "Bob", appCtx.getBean("personA").name

        bb = new BeanBuilder()
        bb.beans {
            personA(AdvisedPerson) {
                name = "Fred"
            }
        }
        bb.registerBeans(appCtx)

        assertEquals "Fred", appCtx.getBean("personA").name
    }

    @Test
    void testSingletonPropertyOnBeanDefinition() {
        def bb = new BeanBuilder()
        bb.beans {
            singletonBean(Bean1) { bean ->
                bean.singleton = true
            }
            nonSingletonBean(Bean1) { bean ->
                bean.singleton = false
            }
            unSpecifiedScopeBean(Bean1)
        }
 
        def ctx = bb.createApplicationContext()
 
        assertTrue ctx.isSingleton('singletonBean'), 'singletonBean should have been a singleton'
        assertFalse ctx.isSingleton('nonSingletonBean'), 'nonSingletonBean should not have been a singleton'
        assertTrue ctx.isSingleton('unSpecifiedScopeBean'), 'unSpecifiedScopeBean should not have been a singleton'
    }
}

class HolyGrailQuest {
    void start() { println "lets begin" }
}

class KnightOfTheRoundTable {
    String name
    String leader
    KnightOfTheRoundTable(String n) {
        this.name = n
    }
    HolyGrailQuest quest

    void embarkOnQuest() {
        quest.start()
    }
}

// simple bean
class Bean1 {
    String person
    int age
    Properties props
    List children
}

// bean referencing other bean
class Bean2 {
    int age
    String person
    Bean1 bean1
    Bean3 bean3
    Properties props
    List children
    Bean1 parent
}

// bean with constructor args
class Bean3 {
    Bean3(String person, Bean1 bean1) {
        this.person = person
        this.bean1 = bean1
    }
    String person
    Bean1 bean1
    int age
}

// bean with factory method
class Bean4 {
    private Bean4() {}
    static Bean4 getInstance() { new Bean4() }
    String person
}

// bean with List-valued constructor arg
class Bean5 {
    Bean5(List<Bean1> people) {
        this.people = people
    }
    List<Bean1> people
}

// bean with Map-valued constructor arg
class Bean6 {
    Bean6(Map<String, Bean1> peopleByName) {
        this.peopleByName = peopleByName
    }
    Map<String, Bean1> peopleByName
}

// a factory bean
class Bean1Factory {
    Bean1 newInstance() { new Bean1() }
}

class ScopeTest {}

class TestScope implements Scope {

    int instanceCount

    Object remove(String name) {
         // do nothing
    }

    void registerDestructionCallback(String name, Runnable callback) {}

    String getConversationId() { "mock" }

    Object get(String name, ObjectFactory<?> objectFactory) {
        instanceCount++
        objectFactory.getObject()
    }

    Object resolveContextualObject(String s) { null }
}

class BirthdayCardSender {
   List peopleSentCards = []
   void onBirthday(AdvisedPerson person) {
      peopleSentCards << person
   }
}

@Component(value = "person")
class AdvisedPerson {
    int age
    String name

    void birthday() {
        ++age
    }
}
// a factory bean that takes arguments
class Bean1FactoryWithArgs {
    Bean1 newInstance(String name) {
        new Bean1(person:name)
    }
}
