<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<html>
    <head>
        <meta name="layout" content="main" />
    </head>
    <body>
        <form:form modelAttribute="person" method="post">
            <table>
                <tr>
                    <td>Name:</td>
                    <td><form:input path="name" /></td>
                    <td><form:errors path="name" /></td>
                </tr>
                <tr>
                    <td>Age:</td>
                    <td><form:input path="age" /></td>
                    <td><form:errors path="age" /></td>
                </tr>
                <tr>
                    <td><button type="submit">Submit</button></td>
                </tr>
            </table>
        </form:form>
    </body>
</html>
