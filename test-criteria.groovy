import org.apache.grails.data.testing.tck.domains.TestEntity
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager

def manager = new GrailsDataCoreTckManager()
manager.setupClasses([TestEntity])
def session = manager.getSession()

new TestEntity(name: "Bob", age: 40).save(flush: true)
new TestEntity(name: "Fred", age: 41).save(flush: true)

println "Data saved. Total: " + TestEntity.count()

def builder = new grails.gorm.CriteriaBuilder(TestEntity, session)
def closure = {
    or {
        like('name', 'B%')
        eq('age', 40)
    }
}
def result1 = builder.invokeMethod("call", [closure] as Object[])
println "Result with invokeMethod: " + result1

def builder2 = new grails.gorm.CriteriaBuilder(TestEntity, session)
def result2 = builder2.list(closure)
println "Result with list(): " + result2

manager.cleanup()
