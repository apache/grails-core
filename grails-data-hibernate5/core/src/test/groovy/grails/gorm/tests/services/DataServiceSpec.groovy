package grails.gorm.tests.services

import grails.gorm.annotation.Entity
import grails.gorm.services.Join
import grails.gorm.services.Query
import grails.gorm.services.Service
import grails.gorm.services.Where
import grails.gorm.transactions.Rollback
import grails.gorm.validation.PersistentEntityValidator
import grails.validation.ValidationException
import groovy.json.DefaultJsonGenerator
import groovy.json.JsonGenerator
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.context.support.StaticMessageSource
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 07/04/2017.
 */
@Rollback
class DataServiceSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())


    void "test inter service interaction"() {
        given:
        Product p1 = new Product(name: "Apple", type:"Fruit").save(flush:true)
        Product p2 = new Product(name: "Orange", type:"Fruit").save(flush:true)
        AnotherProductService productService = datastore.getService(AnotherProductService)

        expect:
        productService.findProductInfo("Apple", "Fruit").name == "Apple"

    }

    void "test list products"() {
        given:
        Product p1 = new Product(name: "Apple", type:"Fruit").save(flush:true)
        Product p2 = new Product(name: "Orange", type:"Fruit").save(flush:true)
        ProductService productService = datastore.getService(ProductService)

        expect:
        productService.listWithArgs(max:1).size() == 1
        productService.listProducts().size() == 2
        productService.listMoreProducts().length == 2
        productService.findEvenMoreProducts().iterator().hasNext()
        productService.findByName("Apple").iterator().hasNext()
        productService.findProducts("Apple", "Fruit").iterator().hasNext()
        !productService.findProducts("Apple", "Devices").iterator().hasNext()
        !productService.findByName("Banana").iterator().hasNext()
        productService.findProducts("Apple").iterator().hasNext()
        !productService.findProducts("Banana").iterator().hasNext()
        productService.getByName("Apple") != null
        productService.getByName("Apple").name == "Apple"
        productService.getByName("Banana") == null
        p1.name == productService.get(p1.id)?.name
        productService.get(100) == null
        productService.find("Apple", "Fruit") != null
        productService.find("Orange", "Fruit").name == "Orange"
        productService.find("Apple", "Fruit", [max:2]) != null
        productService.find("Apple", "Device") == null
    }

    void "test delete by id implementation"() {
        given:
        Product p1 = new Product(name: "Apple", type:"Fruit").save(flush:true)
        Product p2 = new Product(name: "Orange", type:"Fruit").save(flush:true)
        ProductService productService = datastore.getService(ProductService)

        when:
        Product found = productService.get(p1.id)

        then:
        found != null

        when:
        Product deleted = productService.deleteProduct(found.id)

        then:
        deleted != null
        productService.get(found.id) == null

    }

    void "test delete by parameter query implementation"() {
        given:
        Product p1 = new Product(name: "Apple", type:"Fruit").save(flush:true)
        Product p2 = new Product(name: "Orange", type:"Fruit").save(flush:true)
        ProductService productService = datastore.getService(ProductService)

        when:
        Product found = productService.get(p1.id)

        then:
        found != null

        when:
        Product deleted = productService.delete("Apple")
        datastore.sessionFactory.currentSession.flush()

        then:
        deleted != null
        productService.getByName(deleted.name) == null

    }

    void "test delete all implementation"() {
        given:
        Product p1 = new Product(name: "Apple", type:"Fruit").save(flush:true)
        Product p2 = new Product(name: "Orange", type:"Fruit").save(flush:true)
        ProductService productService = datastore.getService(ProductService)

        when:
        Product found = productService.get(p1.id)

        then:
        found != null

        when:
        datastore.sessionFactory.currentSession.clear()
        Number deleted = productService.deleteProducts("Apple")

        then:
        deleted == 1
        productService.get(p1.id) == null

    }

    void "test delete with void return type"() {
        given:
        Product p1 = new Product(name: "Apple", type:"Fruit").save(flush:true)
        Product p2 = new Product(name: "Orange", type:"Fruit").save(flush:true)
        ProductService productService = datastore.getService(ProductService)

        when:
        Product found = productService.get(p1.id)

        then:
        found != null

        when:
        Number deleted = productService.remove(p1.id)

        then:
        deleted == 1
        productService.get(p1.id) == null
    }

    void "test save entity"() {
        given:
        ProductService productService = datastore.getService(ProductService)

        when:
        productService.saveProduct("Pineapple", "Fruit")

        then:
        productService.find("Pineapple", "Fruit") != null
    }

    void "test save invalid entity"() {
        given:
        def mappingContext = datastore.mappingContext
        def entity = mappingContext.getPersistentEntity(Product.name)
        def messageSource = new StaticMessageSource()
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(messageSource), mappingContext, Collections.emptyMap())
        mappingContext.addEntityValidator(
                entity,
                new PersistentEntityValidator(entity, messageSource, evaluator)
        )
        ProductService productService = datastore.getService(ProductService)

        when:
        productService.saveProduct("", "Fruit")

        then:
        thrown(ValidationException)
    }

    void "test abstract class service impl"() {
        given:
        AnotherProductService productService = (AnotherProductService)datastore.getService(AnotherProductInterface)

        when:
        Product p = productService.saveProduct("Apple", "Fruit")

        then:
        datastore.getService(AnotherProductService) != null
        p.id != null
        productService.get(p.id) != null

        when:
        productService.delete(p.id)

        then:
        productService.get(p.id) == null
        productService.getByName("blah").name == "BLAH"

    }

    void "test update one method"() {
        given:
        ProductService productService = datastore.getService(ProductService)

        when:
        productService.saveProduct("Tomato", "Vegetable")

        then:
        productService.find("Tomato", "Vegetable") != null

        when:
        Product product = productService.find("Tomato", "Vegetable")
        productService.updateProduct(product.id, "Fruit")
        datastore.currentSession.flush()

        then:
        productService.find("Tomato", "Vegetable") == null
        productService.find("Tomato", "Fruit") != null

    }

    void 'test property projection'() {
        given:
        ProductService productService = datastore.getService(ProductService)

        when:
        Product p = productService.saveProduct("Tomato", "Vegetable")

        then:
        productService.findProductType(p.id) == "Vegetable"
    }

    void 'test property projection return all types'() {
        given:
        ProductService productService = datastore.getService(ProductService)

        when:
        productService.saveProduct("Carrot", "Vegetable")
        productService.saveProduct("Pumpkin", "Vegetable")
        productService.saveProduct("Tomato", "Fruit")

        then:
        productService.listProductName("Vegetable").size() == 2
        productService.countProducts("Vegetable") == 2
        productService.countPrimProducts("Vegetable") == 2
        productService.countByType("Vegetable") == 2
    }

    void "test @where annotation"() {
        given:
        ProductService productService = datastore.getService(ProductService)

        when:
        productService.saveProduct("Carrot", "Vegetable")
        productService.saveProduct("Pumpkin", "Vegetable")
        productService.saveProduct("Tomato", "Fruit")

        Product p = productService.searchByType("Veg%")

        then:
        p != null
        p.name == 'Carrot'
        productService.searchByType("Stuf%") == null
        productService.searchProducts("Veg%").size() == 2
        productService.howManyProducts("Veg%") == 2

    }

    void "test @query annotation"() {
        given:
        ProductService productService = datastore.getService(ProductService)

        productService.saveProduct("Carrot", "Vegetable")
        productService.saveProduct("Pumpkin", "Vegetable")
        productService.saveProduct("Tomato", "Fruit")


        when:
        Product product = productService.searchWithQuery("Carr%")

        then:
        product != null
        product.name == "Carrot"
        productService.searchProductType("Carr%") == "Vegetable"

        when:
        List<Product> results = productService.searchAllWithQuery("Veg%")

        then:
        results.size() == 2

        when:
        List<String> names = productService.searchProductNames("Ve%")

        then:
        names.size() == 2
        names == ["Carrot", "Pumpkin"]

    }

    void "test interface projection"() {
        given:
        ProductService productService = datastore.getService(ProductService)

        when:
        productService.saveProduct("Carrot", "Vegetable")
        productService.saveProduct("Pumpkin", "Vegetable")
        productService.saveProduct("Tomato", "Fruit")

        ProductInfo info = productService.findProductInfo("Pumpkin", "Vegetable")
        List<ProductInfo> infos = productService.findProductInfos( "Vegetable")
        def result = new DefaultJsonGenerator(new JsonGenerator.Options().excludeFieldsByName("\$target")).toJson(info)
        then:
        infos.size() == 2
        infos.first().name == "Carrot"
        result == '{"name":"Pumpkin"}'
        info != null
        info.name == "Pumpkin"
        productService.searchProductInfoByName("Pump%") != null
        productService.findByTypeLike("Veg%") != null
        productService.findByTypeLike("Jun%")  == null
        productService.findAllByTypeLike( "Vege%").size() == 2

        when:
        info = productService.searchProductInfo("Pum%")

        then:
        info.name == "Pumpkin"

        when:
        datastore.sessionFactory.currentSession.clear()
        productService.deleteSomeProducts("Vegetable")


        then:
        productService.findByTypeLike("Vege%") == null


    }

    void "test join query on attributes with @Query"() {
        given:
        ProductService productService = datastore.getService(ProductService)
        new Product(name: "Apple", type: "Fruit")
                .addToAttributes(name: "round")
                .save(flush:true)
        new Product(name: "Banana", type: "Fruit")
                .addToAttributes(name: "curved")
                .save(flush:true)

        when:
        List<Product> products = productService.findProductsWithAttributes("round")

        then:
        products.size() == 1
        products[0].name == "Apple"
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/960')
    void "test findBy dynamic finder with @Join doesn't return proxies"() {
        given:
        ProductService productService = datastore.getService(ProductService)
        new Product(name: "Apple", type: "Fruit")
                .addToAttributes(name: "round")
                .save(flush:true)

        new Product(name: "Banana", type: "Fruit")
                .addToAttributes(name: "curved")
                .save(flush:true)

        datastore.currentSession.clear()

        when:
        Product product = productService.findByName("Apple").first()

        then:
        product.attributes.isInitialized()
    }
}

interface ProductInfo {
    String getName()
}

@Entity
class Product {
    String name
    String type

    static hasMany = [attributes:Attribute]

    static constraints = {
        name blank:false
    }
}

@Entity
class Attribute {
    String name
}

interface AnotherProductInterface {
    Product saveProduct(String name, String type)

    Number delete(Serializable id)
}

@Service(Product)
abstract class AnotherProductService implements AnotherProductInterface{

    ProductService originalProductService

    abstract Product get(Serializable id)

    Product getByName(String name) {
        return new Product(name:name.toUpperCase())
    }

    ProductInfo findProductInfo(String name, String type) {
        getOriginalProductService().findProductInfo(name, type) // ?
    }
}

@Service(Product)
interface ProductService {
    List<ProductInfo> findProductInfos(String type)

    List<ProductInfo> findAllByTypeLike(String type)

    ProductInfo findProductInfo(String name, String type)

    @Query("""
    SELECT $p FROM ${Product p} JOIN ${Attribute attr = p.attributes} WHERE ${attr.name} = $name
    """)
    List<Product> findProductsWithAttributes(String name)

    @Query("from ${Product p} where $p.name like $pattern")
    ProductInfo searchProductInfo(String pattern)

    ProductInfo findByTypeLike(String type)

    @Where({ name ==~ pattern })
    ProductInfo searchProductInfoByName(String pattern)

    @Query("from ${Product p} where $p.name like $pattern")
    Product searchWithQuery(String pattern)

    @Query("select ${p.type} from ${Product p} where $p.name like $pattern")
    String searchProductType(String pattern)

    @Query("from ${Product p} where $p.type like $pattern")
    List<Product> searchAllWithQuery(String pattern)

    @Query("select $p.name from ${Product p} where $p.type like $pattern")
    List<String> searchProductNames(String pattern)

    @Where({ type ==~ pattern })
    Product searchByType(String pattern)

    @Where({ type ==~ pattern })
    Set<Product> searchProducts(String pattern)

    @Where({ type ==~ pattern })
    Number howManyProducts(String pattern)

    List<String> listProductName(String type)

    String findProductType(Serializable id)

    Number countByType(String t)

    Number countProducts(String type)

    int countPrimProducts(String type)

    Product updateProduct(Serializable id, String type)

    Product saveProduct(String name, String type)

    Number deleteProducts(String name)

    @Where({ type == type })
    Number deleteSomeProducts(String type)
    Product delete(String name)

    Number remove(Serializable id)

    Product deleteProduct(Serializable id)

    Product get(Serializable id)

    Product getByName(String name)

    Product find(String name, String type)

    Product find(String name, String type, Map args)

    @Join('attributes')
    List<Product> findProducts(String name)

    List<Product> findProducts(String name, String type)

    List<Product> listWithArgs(Map args)

    List<Product> listProducts()

    Product[] listMoreProducts()

    Iterable<Product> findEvenMoreProducts()

    @Join('attributes')
    Iterable<Product> findByName(String n)
}
