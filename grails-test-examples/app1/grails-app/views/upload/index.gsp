<!DOCTYPE html>
<html>
<head>
    <title>File upload</title>
</head>
<body>
    ${flash.message}
    <form id="myForm" action="/upload/upload" method="POST" enctype="multipart/form-data">
        <input type="file" name="myFile" />
        <input id="input1" type="submit" name="submit"/>
    </form>

    <form id="myForm2" action="/upload/upload2" method="POST" enctype="multipart/form-data">
        <input type="file" name="myFile" />
        <input id="input2" type="submit" name="submit"/>
    </form>
</body>
</html>
