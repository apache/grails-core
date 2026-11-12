import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.provisioning.InMemoryUserDetailsManager

class BootStrap {

    InMemoryUserDetailsManager userDetailsService

    def init = { servletContext ->
        UserDetails jimi = new User('jimi', '{noop}jimispassword', [new SimpleGrantedAuthority('ROLE_USER'), new SimpleGrantedAuthority('ROLE_ADMIN')])
        userDetailsService.createUser(jimi)

        UserDetails alvaro = new User('115537660854424164575', '{noop}N/A', [new SimpleGrantedAuthority('ROLE_USER'), new SimpleGrantedAuthority('ROLE_ADMIN')])
        userDetailsService.createUser(alvaro)
    }

    def destroy = {
    }
}
