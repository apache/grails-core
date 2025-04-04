package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec

import java.sql.ResultSet

/**
 * Created by graemerocher on 24/02/16.
 */
class EnumMappingSpec extends GormSpec {

    void "Test enum mapping"() {
        when:"An enum property is persisted"
        new Recipe(title: "Chicken Tikka Masala").save(flush:true)
        def resultSet = sessionFactory.currentSession.connection().prepareStatement("select * from recipe").executeQuery()
        resultSet.next()

        then:"The enum is mapped as a varchar"
        resultSet.getString('type') == 'GOOD'

    }
    @Override
    List getDomainClasses() {
        [Recipe]
    }
}

@Entity
class Recipe {
    String title
    RecipeType type = RecipeType.GOOD
}
enum RecipeType{
    GOOD, BAD, BORING
}