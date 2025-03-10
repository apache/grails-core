<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<html>
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
        <g:applyLayout name="sample">
            Text to decorate.
        </g:applyLayout>
    </body>
</html>