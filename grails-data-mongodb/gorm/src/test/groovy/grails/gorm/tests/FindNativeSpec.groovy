package grails.gorm.tests

import com.mongodb.Block
import org.bson.Document
import org.grails.datastore.gorm.mongo.Product
//tag::nativeImport[]
import com.mongodb.client.FindIterable
import static com.mongodb.client.model.Filters.*
//end::nativeImport[]
/**
 * Created by graemerocher on 24/10/16.
 */
class FindNativeSpec extends GormDatastoreSpec {


    void "test native find method"() {
        setup:
        Product.DB.drop()
        new Product(title: "cake").save()
        new Product(title: "coffee").save(flush:true)

        when:"the native find method is used"
        //tag::nativeFind[]
        FindIterable findIterable = Product.find(eq("title", "coffee"))
        findIterable.limit(10)
                    .each { Product product ->
            println "Product title $product.title"
        }
        //end::nativeFind[]

        //tag::collectionFind[]
        Document doc = Product.collection
                                .find(eq("title", "coffee"))
                                .first()

        //end::collectionFind[]

        then:"The results are correct"
        findIterable.size() == 1
        doc != null

        when:"findAndDeleteOne is used"
        Product p = Product.findOneAndDelete(eq("title", "coffee"))

        then:"The right one was deleted"
        Product.count == 1
        Product.findByTitle("cake")
        p.title == 'coffee'
    }



    @Override
    List getDomainClasses() {
        [Product]
    }
}
