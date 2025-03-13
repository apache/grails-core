import functional.tests.*
import jakarta.servlet.ServletContext

class BootStrap {

    ServletContext servletContext

    def init = {
    	Book.DB.drop()
    }

    def destroy = {
    }
}
