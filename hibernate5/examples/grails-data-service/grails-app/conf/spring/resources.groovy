import example.ClassUsingAService
import example.TestBean

// Place your Spring DSL code here
beans = {

    classUsingAService(ClassUsingAService) {
        testService = ref('testService')
    }

    testBean(TestBean)
}
