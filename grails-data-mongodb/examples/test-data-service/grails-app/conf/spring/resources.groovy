// Place your Spring DSL code here
//noinspection GrPackage
beans = {

    restAuthenticationSuccessHandler(example.LoginAuthenticationSucessHandler) {
        testService = ref('testService')
    }

    testBean(example.TestBean)
}
