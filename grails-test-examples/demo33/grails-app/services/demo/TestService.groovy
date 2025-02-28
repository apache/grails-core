package demo

import org.springframework.beans.factory.annotation.Value

class TestService {

    @Value('${demo.foo}')
    String foo
}
