import org.springframework.security.provisioning.InMemoryUserDetailsManager

// Place your Spring DSL code here
beans = {

    userDetailsService(InMemoryUserDetailsManager, [])

    //passwordEncoder(PlaintextPasswordEncoder)


}