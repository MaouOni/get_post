function HeadTest(myLabel){
    var http = new XMLHttpRequest();
    var url = '/?archivo=' + myLabel + "*";

    http.open('HEAD', url);
    http.onreadystatechange = function() {
        if (http.readyState === XMLHttpRequest.HEADERS_RECEIVED) {
            const contentLength = http.getResponseHeader("Content-Length");
            if (contentLength > 0) {
                alert("Tamaño: " + contentLength + " Bytes.");
            } else {
                alert("El archivo no existe.");
            }
        }
    };
    http.send();
}

function HeadTestOtro(){
    HeadTest(document.getElementById('NombreArchivo').value);
}

function PutTest(url, callback){
    var http = new XMLHttpRequest();
    http.open('PUT', url);
    http.onreadystatechange = function() {
        if (this.readyState === XMLHttpRequest.DONE) {
            callback(this.status !== 404);
        }
        if (http.readyState === XMLHttpRequest.HEADERS_RECEIVED) {
            alert("¡El servidor terminó el proceso!");
        }
    };
    http.send();
}
