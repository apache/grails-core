package grails.gorm.services

import grails.gorm.multitenancy.TenantService
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.TransactionService
import grails.gorm.transactions.Transactional
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.datastore.gorm.services.Implemented
import org.grails.datastore.gorm.services.implementers.FindAllImplementer
import org.grails.datastore.gorm.services.implementers.FindOneImplementer
import org.grails.datastore.gorm.services.implementers.FindOneInterfaceProjectionImplementer
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.services.DefaultServiceRegistry
import org.grails.datastore.mapping.services.ServiceRegistry
import spock.lang.Specification

/**
 * Created by graemerocher on 11/01/2017.
 */
class ServiceTransformSpec extends Specification {

    void "test interface projection with an entity that implements GormEntity"() {
        when:
        def klass = new GroovyClassLoader().parseClass("""
            import grails.gorm.services.Service
            import grails.gorm.annotation.Entity
            import org.grails.datastore.gorm.GormEntity
            
            @Entity
            class X implements GormEntity<X> {
                String a
                String b
            }
            
            interface IX {
                String getB()
            }
            
            @Service(X)
            interface XService {
                IX getX(String a)
            }
        """.stripIndent())

        then:
        noExceptionThrown()

        and:
        def impl = klass.classLoader.loadClass("\$XServiceImplementation")
        impl != null

        and:
        impl.getMethod("getX", String).getAnnotation(Implemented).by() == FindOneInterfaceProjectionImplementer
    }

    void "test interface projection with an entity that implements a marker interface"() {
        when:
        def klass = new GroovyClassLoader().parseClass("""
            import grails.gorm.services.Service
            import grails.gorm.annotation.Entity
            
            // an interface unrelated to projections
            interface HasTitle {
                String getTitle()
            }
            
            @Entity
            class Article implements HasTitle {
                String title
                String subtitle
            }
            
            interface ArticleInfo {
                String getSubtitle()
            }
            
            @Service(Article)
            interface ArticleService {
                ArticleInfo getArticle(String title)
            }
        """.stripIndent())

        then:
        noExceptionThrown()

        and:
        klass != null

        and:
        def impl = klass.classLoader.loadClass("\$ArticleServiceImplementation")
        impl != null

        and:
        impl.getMethod("getArticle", String).getAnnotation(Implemented).by() == FindOneInterfaceProjectionImplementer
    }

    void "test interface projection that intersects with an interface implemented by the entity"() {
        when:
        def klass = new GroovyClassLoader().parseClass("""
            import grails.gorm.services.Service
            import grails.gorm.annotation.Entity
            
            // an interface unrelated to projections
            interface HasSubtitle {
                String getSubtitle()
            }
            
            @Entity
            class BlogPost implements HasSubtitle {
                String title
                String subtitle
            }
            
            interface BlogPostInfo {
                String getSubtitle()
            }
            
            @Service(BlogPost)
            interface BlogPostService {
                BlogPostInfo getBlogPost(String title)
            }
        """.stripIndent())

        then:
        noExceptionThrown()

        and:
        klass != null

        and:
        def impl = klass.classLoader.loadClass("\$BlogPostServiceImplementation")
        impl != null

        and:
        impl.getMethod("getBlogPost", String).getAnnotation(Implemented).by() == FindOneInterfaceProjectionImplementer
    }

    void "test service transformation with @CurrentTenant"() {
        when:
        Class bookService =new GroovyClassLoader().parseClass('''
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Book)
@CurrentTenant
interface BookService {

    Book find(Serializable id)

    Book saveBook(String title)

}
@Entity
class Book {
    String title
}
return BookService
''')
        Class impl = bookService.classLoader.loadClass("\$BookServiceImplementation")

        then:"The service was transformed correctly"
        impl.getMethod("find", Serializable).getAnnotation(Implemented).by() == FindOneImplementer
    }

    void "test service transform on abstract protected methods"() {
        when:"The service transform is applied to an abstract class"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(name="fooService", value=Foo)
abstract class AbstractMyService implements FooService {

    AbstractMyService anotherFooService 
    
    protected abstract Foo findFoo(Serializable id)
    
    Foo readFoo(Serializable id) {
        findOne(id)
    }
    
    @Override
    Foo delete(Serializable id) {
        def foo = Foo.get(id)
        foo?.delete()
        foo?.title = "DELETED"
        return foo
    }
}
 
interface FooService {
    Foo delete(Serializable id)
}
@Entity
class Foo {
    String title
}

''')
        then:
        !service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$AbstractMyServiceImplementation")

        then:"The impl is valid - protected methods should have no transaction"
        impl.getMethod("readFoo", Serializable).getAnnotation(ReadOnly) != null
        impl.getMethod("findFoo", Serializable).getAnnotation(ReadOnly) == null
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)

    }

    void "test dynamic finder interface projection"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    IFoo findByNameLike(String n)
}
@Entity
class Foo {
    String title
    String name
}

interface IFoo {
    String getTitle()
}
''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("findByNameLike", String).getAnnotation(ReadOnly) != null
    }

    void "test dynamic finder interface projection that returns a list"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    List<IFoo> findByNameLike(String n)
}
@Entity
class Foo {
    String title
    String name
}

interface IFoo {
    String getTitle()
}
''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("findByNameLike", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection with @Query"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    @Query("from $Foo as f where f.title like $title")
    IFoo search(String title)
}
@Entity
class Foo {
    String title
    String name
}

interface IFoo {
    String getTitle()
}
''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("search", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    IFoo find(String title)
}
@Entity
class Foo {
    String title
    String name
}

interface IFoo {
    String getTitle()
}
''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("find", String).getAnnotation(ReadOnly) != null
    }

    void "test interface projection that returns a list"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    List<IFoo> find(String title)
}
@Entity
class Foo {
    String title
    String name
}

interface IFoo {
    String getTitle()
}
''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("find", String).getAnnotation(ReadOnly) != null
    }


    void "test count method"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    Number count(String title)
    
    Integer countFoos(String title)
    
    int countPrimitive(String title)
    
    int countAll()
}
@Entity
class Foo {
    String title
    String name
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("count", String).getAnnotation(ReadOnly) != null
    }

    void "test countBy method"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    Number countByTitle(String t)
    
    Integer countName(String name)
}
@Entity
class Foo {
    String title
    String name
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("countByTitle", String).getAnnotation(ReadOnly) != null
    }

    void "test simple list method"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    List<Foo> listFoos()
    
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("listFoos").getAnnotation(ReadOnly) != null
        impl.getMethod("listFoos").getAnnotation(Implemented).by() == FindAllImplementer
    }

    void "test @Join on finder"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import static jakarta.persistence.criteria.JoinType.*

@Service(Foo)
interface MyService {
    @Join('bars')
    Foo find(String title)
    
    @Join(value='bars', type=LEFT)
    Foo findFoo(String title)
    
}
@Entity
class Foo {
    String title
    static hasMany = [bars:Bar]
}
@Entity
class Bar {
    
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("find", String).getAnnotation(ReadOnly) != null
    }

    void "test @Query invalid property"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("from $Foo as f where f.title like $wrong") 
    Foo searchByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains '''[Static type checking] - The variable [wrong] is undeclared.
 @ line 8, column 48.
   $Foo as f where f.title like $wrong")'''
    }

    void "test @Query invalid domain"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("from $String as f where f.title like $pattern") 
    Foo searchByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains '''Invalid query class [java.lang.String]. Referenced classes in queries must be domain classes
 @ line 8, column 19.
       @Query("from $String as f where f.title like $pattern") 
                     ^'''
    }

    void "test simple @Query annotation"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("from $Foo as f where f.title like $pattern") 
    Foo searchByTitle(String pattern)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }

    void "test @Query annotation with projection"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("select max(${f.age}) from ${Foo f} where f.title like $pattern") 
    int searchByTitle(String pattern)
}
@Entity
class Foo {
    String title
    int age
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }


    void "test @Query update annotation"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("update ${Foo foo} set ${foo.title} = $newTitle where $foo.title = $oldTitle") 
    Number updateTitle(String newTitle, String oldTitle)
    
    @Query("delete ${Foo foo} where $foo.title = $title")
    void kill(String title)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("updateTitle", String, String).getAnnotation(Transactional)
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }

    void "test @Query update annotation using id attribute"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("update ${Foo foo} set ${foo.title} = $newTitle where $foo.id = $id") 
    Number updateTitle(String newTitle, Long id)
    
    @Query("delete ${Foo foo} where $foo.title = $title")
    void kill(String title)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("updateTitle", String, Long).getAnnotation(Transactional)
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }


    void "test @Query update annotation with default transaction attributes at class level"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity
import grails.gorm.transactions.*
@Service(Foo)
@Transactional("foo")
interface MyService {

    @Query("update ${Foo foo} set ${foo.title} = $newTitle where $foo.title = $oldTitle") 
    Number updateTitle(String newTitle, String oldTitle)
    
    @Query("delete ${Foo foo} where $foo.title = $title")
    void kill(String title)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")
        def instance = impl.newInstance()

        then:"The impl is valid"
        impl.getAnnotation(Transactional).value() == "foo"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)

        when:
        instance.kill("blah")

        then:
        thrown(IllegalStateException)
    }

    void "test @Query annotation with declared variables"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("select $f.title from ${Foo f} where $f.title like $pattern") 
    List<String> searchByTitle(String pattern)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }


    void "test @Query invalid variable property"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Query("from ${Foo f} where $f.tit like $pattern") 
    Foo searchByTitle(String pattern)
}
@Entity
class Foo {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains '''No property [tit] existing for domain class [Foo]
 @ line 8, column 34.
       @Query("from ${Foo f} where $f.tit like $pattern") 
                                    ^'''
    }

    void 'test @Where annotation'() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {

    @Where({ title ==~ pattern  }) 
    Foo searchByTitle(String pattern)
    
    @Where({ title ==~ pattern })
    Set<Foo> searchFoos(String pattern)

}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)
    }

    void "test implement abstract class"() {
        when:"The service transform is applied to an abstract class"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
abstract class AbstractMyService implements MyService {

    Foo readFoo(Serializable id) {
        Foo.read(id)
    }
    
    @Override
    Foo delete(Serializable id) {
        def foo = Foo.get(id)
        foo?.delete()
        foo?.title = "DELETED"
        return foo
    }
}
 
interface MyService {
    Number deleteMoreFoos(String title)
    
    void deleteFoos(String title)
//    Foo get(Serializable id)
    
    Foo delete(Serializable id)
    
    List<Foo> listFoos()
    
    Foo[] listMoreFoos()
    
    Iterable<Foo> listEvenMoreFoos()
    
    List<Foo> findByTitle(String title)
    
    List<Foo> findByTitleLike(String title)
    
    Foo saveFoo(String title)
}
@Entity
class Foo {
    String title
}

''')
        then:
        !service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$AbstractMyServiceImplementation")

        then:"The impl is valid"
        impl.getMethod("deleteMoreFoos", String).getAnnotation(Transactional) != null
        impl.getMethod("delete", Serializable).getAnnotation(Transactional) != null
        impl.getMethod("readFoo", Serializable).getAnnotation(ReadOnly) != null
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)

        when:
        impl.newInstance().listFoos()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No GORM implementations configured. Ensure GORM has been initialized correctly'
    }

    void "test implement interface"() {
        when:"The service transform is applied to an interface it can't implement"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {
    Number deleteMoreFoos(String title)
    
    void deleteFoos(String title)
//    Foo get(Serializable id)
    
    Foo delete(Serializable id)
    
    List<Foo> listFoos()
    
    Foo[] listMoreFoos()
    
    Iterable<Foo> listEvenMoreFoos()
    
    List<Foo> findByTitle(String title)
    
    List<Foo> findByTitleLike(String title)
    
    Foo saveFoo(String title)
}
@Entity
class Foo {
    String title
}

''')

        then:
        service.isInterface()
        println service.classLoader.loadedClasses

        when:"the impl is obtained"
        Class impl = service.classLoader.loadClass("\$MyServiceImplementation")

        then:"The impl is valid"
        impl.getAnnotation(Service) != null
        impl.getAnnotation(Service).name() == 'myService'
        impl.getMethod("deleteMoreFoos", String).getAnnotation(Transactional) != null
        org.grails.datastore.mapping.services.Service.isAssignableFrom(impl)

        when:
        impl.newInstance().listFoos()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No GORM implementations configured. Ensure GORM has been initialized correctly'
    }

    void "test service transform applied to interface that can't be implemented"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*

@Service
interface MyService {
    void foo()
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains '''No implementations possible for method 'void foo()'. Please use an abstract class instead and provide an implementation.
 @ line 6, column 5.
       void foo()
       ^'''
    }

    void "test service transform applied with a dynamic finder for a non-existent property"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(expose=false)
interface MyService {
    
    List<Foo> findByTitLike(String title)
}
@Entity
class Foo {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains '''Cannot implement finder for non-existent property [tit] of class [Foo]
 @ line 8, column 5.
       List<Foo> findByTitLike(String title)'''
    }


    void "test service transform applied with a dynamic finder for a property of the wrong type"() {
        when:"The service transform is applied to an interface it can't implement"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(expose=false)
interface MyService {
    
    List<Foo> find(Integer title)
}
@Entity
class Foo {
    String title
}
''')

        then:"A compilation error occurred"
        def e = thrown(MultipleCompilationErrorsException)
        e.message.normalize().contains '''Cannot implement method for argument [title]. No property exists on domain class [Foo]
 @ line 8, column 5.
       List<Foo> find(Integer title)
       ^'''
    }

    void "test service transform"() {
        given:
        ServiceRegistry reg = new DefaultServiceRegistry(Mock(Datastore), false)
        reg.initialize()
        expect:
        org.grails.datastore.mapping.services.Service.isAssignableFrom(TestService)
        reg.getService(TestService) != null
        reg.getService(TestService2) != null
        reg.getService(TestService).datastore != null
        reg.getService(TransactionService) != null
        reg.getService(TenantService) != null
    }
}

@Service
class TestService {
    void doStuff() {
    }
}

@Service
class TestService2 {
    void doStuff() {
    }
}
