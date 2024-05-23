package com.example;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

class WebServer implements Runnable {
    protected Socket socket;
    protected DataOutputStream dos;
    protected DataInputStream dis;
    protected String FileName;

    public WebServer(Socket _socket) {
        this.socket = _socket;
    }

    public void run() {
        try {
            initializeStreams();
            String peticion = readRequest();

            System.out.println("\nCliente Conectado desde: " + socket.getInetAddress());
            System.out.println("Por el puerto: " + socket.getPort());
            System.out.println("Datos: " + peticion + "\r\n\r\n");

            handleRequest(peticion);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeStreams() throws IOException {
        dos = new DataOutputStream(socket.getOutputStream());
        dis = new DataInputStream(socket.getInputStream());
    }

    private String readRequest() throws IOException {
        byte[] b = new byte[1024];
        int t = dis.read(b);
        return new String(b, 0, t);
    }

    private void sendEmptyResponse() throws IOException {
        dos.write("<html><head><title>Servidor WEB</title></head><body bgcolor=\"#AACCFF\"><br>Linea Vacia</br></body></html>\n".getBytes());
        dos.flush();
        socket.close();
    }

    private void handleRequest(String peticion) throws IOException, InterruptedException {
        StringTokenizer st1 = new StringTokenizer(peticion, "\n");
        String line = st1.nextToken();
        switch (line.toUpperCase().split(" ")[0]) {
            case "GET":
                handleGetRequest(line);
                break;
            case "POST":
                handlePostRequest(peticion);
                break;
            case "DELETE":
                handleDeleteRequest(line);
                break;
            case "HEAD":
                handleHeadRequest(line);
                break;
            case "PUT":
                handlePutRequest(line);
                break;
            default:
                sendNotImplementedResponse();
        }
    }

    private void handleGetRequest(String line) throws IOException {
        if (!line.contains("?")) {
            getArch(line);
            if (FileName.isEmpty() || FileName.equals("/")) {
                FileName = "index.html";
            }
            sendFile(FileName);
        } else {
            handleGetRequestWithParams(line);
        }
    }

    private void handleGetRequestWithParams(String line) throws IOException {
        StringTokenizer tokens = new StringTokenizer(line, "?");
        tokens.nextToken();
        String req = tokens.nextToken();
        String parametros = req.substring(0, req.indexOf(" ")) + "\n";
        System.out.println("parametros: " + parametros);

        // Parse the parameters
        Map<String, String> paramMap = parseParameters(parametros);

        // Insert data into the database
        boolean success = storeUserData(paramMap);

        String dynamicContent = "<h1>Parámetros obtenidos:</h1><h3><b>" + parametros + "</b></h3>";
        if (!success) {
            dynamicContent = "<h1>Error al almacenar datos</h1>";
        }

        sendResponseWithTemplate(dynamicContent);
    }

    private void handlePostRequest(String peticion) throws IOException {
        String[] datosUsuario = getDatosUsuario(peticion);
        String dynamicContent;

        if (datosUsuario != null) {
            // Fetch user data from the database
            Map<String, String> userData = fetchUserData(datosUsuario[0], datosUsuario[1]);
            if (userData != null) {
                dynamicContent = generateUserDataHtml(userData);
            } else {
                dynamicContent = "<h1>Usuario o contraseña incorrectos</h1>";
            }
        } else {
            dynamicContent = "<h1>400 Petición Incorrecta</h1>La solicitud no tiene el formato esperado.";
        }

        sendResponseWithTemplate(dynamicContent);
    }

    private Map<String, String> fetchUserData(String username, String password) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, username);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                Map<String, String> userData = new HashMap<>();
                userData.put("User", resultSet.getString("username"));
                userData.put("Pass", resultSet.getString("password"));
                userData.put("Apellido", resultSet.getString("name"));
                userData.put("Direccion", resultSet.getString("address"));
                userData.put("Telefono", resultSet.getString("phone"));
                userData.put("comentario", resultSet.getString("comments"));
                return userData;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String generateUserDataHtml(Map<String, String> userData) {
        StringBuilder html = new StringBuilder();
        html.append("<h1>Datos del usuario:</h1><h3><b>");
        html.append("Usuario: ").append(userData.get("User")).append("<br>");
        html.append("Contraseña: ").append(userData.get("Pass")).append("<br>");
        html.append("Nombre: ").append(userData.get("Apellido")).append("<br>");
        html.append("Dirección: ").append(userData.get("Direccion")).append("<br>");
        html.append("Teléfono: ").append(userData.get("Telefono")).append("<br>");
        html.append("Comentarios: ").append(userData.get("comentario")).append("<br>");
        html.append("</b></h3>");
        return html.toString();
    }

    private boolean storeUserData(Map<String, String> userData) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO users (username, password, name, address, phone, comments) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, userData.get("User"));
            statement.setString(2, userData.get("Pass"));
            statement.setString(3, userData.getOrDefault("Apellido", "")); // Default to empty string if not provided
            statement.setString(4, userData.getOrDefault("Direccion", "")); // Default to empty string if not provided
            statement.setString(5, userData.getOrDefault("Telefono", "")); // Default to empty string if not provided
            statement.setString(6, userData.getOrDefault("comentario", "")); // Default to empty string if not provided
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void handleDeleteRequest(String line) throws IOException {
        String nombreArchivo = getNombreArchivoBorrar(line);
        assert nombreArchivo != null;
        File archivo = new File("src/main/resources/web/" + nombreArchivo);
        boolean eliminado = archivo.delete();
        StringBuffer respuesta;

        if (eliminado) {
            respuesta = createResponseHeader("200 OK", "text/html; charset=utf-8");
            respuesta.append("<html><body><h1>Archivo eliminado correctamente</h1></body></html>\r\n");
        } else {
            respuesta = createResponseHeader("404 Not Found", "text/html; charset=utf-8");
            respuesta.append("<html><body><h1>Error al eliminar el archivo</h1></body></html>\r\n");
        }
        sendResponse(respuesta);
    }

    private void handleHeadRequest(String line) throws IOException {
        String nombreArchivo = getNombreArchivo(line);
        File ff = new File("src/main/resources/web/" + nombreArchivo);
        StringBuffer respuesta = new StringBuffer();

        if (ff.exists()) {
            long tam_archivo = ff.length();
            respuesta.append("HTTP/1.0 200 OK \n");
            respuesta.append("Date: ").append(new Date()).append(" \n");
            respuesta.append("Content-Type: ").append(Files.probeContentType(ff.toPath())).append(" \n");
            respuesta.append("Content-Length: ").append(tam_archivo).append(" \n\n");
        } else {
            respuesta.append("HTTP/1.0 404 Not Found \n");
            respuesta.append("Date: ").append(new Date()).append(" \n");
            respuesta.append("Content-Type: text/plain \n\n");
        }
        dos.write(respuesta.toString().getBytes());
        dos.flush();
        dos.close();
        socket.close();
    }

    private void handlePutRequest(String line) throws IOException, InterruptedException {
        String[] putTokens = line.split(" ");
        String fileName = putTokens[1].substring(1); // Elimina el carácter "/" del nombre del archivo
        Thread.sleep(10000);
        Path filePath = Paths.get("src/main/resources/web", fileName);
        Files.copy(dis, filePath, StandardCopyOption.REPLACE_EXISTING);
        StringBuffer respuesta = createResponseHeader("200 Created", "text/html");
        respuesta.append("Content-Length: 0 \n\n").append("SERVIDOR PUT");
        sendResponse(respuesta);
    }

    private void sendNotImplementedResponse() throws IOException {
        dos.write("HTTP/1.0 501 Not Implemented\r\n".getBytes());
        dos.flush();
        dos.close();
        socket.close();
    }

    private StringBuffer createResponseHeader(String status, String contentType) {
        StringBuffer respuesta = new StringBuffer();
        respuesta.append("HTTP/1.0 ").append(status).append(" \n");
        respuesta.append("Date: ").append(new Date()).append(" \n");
        respuesta.append("Content-Type: ").append(contentType).append(" \n\n");
        return respuesta;
    }

    private void sendResponse(StringBuffer respuesta) throws IOException {
        dos.write(respuesta.toString().getBytes());
        dos.flush();
        dos.close();
        socket.close();
    }

    private void sendResponseWithTemplate(String dynamicContent) throws IOException {
        String templatePath = "src/main/resources/web/template.html";
        Path path = Paths.get(templatePath);
        String template = new String(Files.readAllBytes(path));
        String responseContent = template.replace("<!-- Content will be dynamically inserted here -->", dynamicContent);
        dos.writeBytes("HTTP/1.0 200 OK\r\n");
        dos.writeBytes("Content-Type: text/html\r\n");
        dos.writeBytes("Content-Length: " + responseContent.length() + "\r\n");
        dos.writeBytes("\r\n");
        dos.write(responseContent.getBytes());
        dos.flush();
        dos.close();
        socket.close();
    }

    public void getArch(String line) {
        if (line.toUpperCase().startsWith("GET")) {
            int i = line.indexOf("/");
            int f = line.indexOf(" ", i);
            FileName = line.substring(i + 1, f);
        }
    }

    public void sendFile(String fileName) throws IOException {
        Path filePath = Paths.get("src/main/resources/web", fileName);
        if (!Files.exists(filePath)) {
            handleFileNotFound();
            return;
        }

        try {
            byte[] fileBytes = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            dos.writeBytes("HTTP/1.0 200 OK\r\n");
            dos.writeBytes("Content-Type: " + contentType + "\r\n");
            dos.writeBytes("Content-Length: " + fileBytes.length + "\r\n");
            dos.writeBytes("\r\n");
            dos.write(fileBytes);
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            dos.close();
            socket.close();
        }
    }

    private void handleFileNotFound() throws IOException {
        StringBuffer respuesta = createResponseHeader("404 Not Found", "text/html");
        respuesta.append("<html><meta charset=\"utf-8\"></meta><body><h1>404 No se encuentra</h1>No se encontró contexto para la solicitud.</body></html>\n");
        sendResponse(respuesta);
    }

    private String[] getDatosUsuario(String data) {
        String[] datosUsuario = new String[2];
        String temp;
        StringTokenizer st = new StringTokenizer(data, "\r\n");
        boolean userEncontrado = false;
        boolean passEncontrado = false;

        while (st.hasMoreTokens()) {
            temp = st.nextToken();
            if (temp.startsWith("User=")) {
                datosUsuario[0] = temp.split("=").length > 1 ? temp.split("=")[1] : " ";
                userEncontrado = true;
            } else if (temp.startsWith("Pass=")) {
                datosUsuario[1] = temp.split("=").length > 1 ? temp.split("=")[1] : " ";
                passEncontrado = true;
            }
        }

        return (userEncontrado && passEncontrado) ? datosUsuario : null;
    }

    private String getNombreArchivoBorrar(String data) {
        StringTokenizer st = new StringTokenizer(data, " ");
        while (st.hasMoreTokens()) {
            String temp = st.nextToken();
            if (temp.startsWith("/")) {
                String nombreArchivo = temp.substring(1);
                System.out.println("Nombre del archivo: " + nombreArchivo);
                return nombreArchivo;
            }
        }
        return null;
    }

    private String getNombreArchivo(String data) {
        StringTokenizer st = new StringTokenizer(data, "?");
        while (st.hasMoreTokens()) {
            String temp = st.nextToken();
            if (temp.startsWith("archivo=")) {
                String nombreArchivo = temp.substring(0, temp.indexOf("*")).split("=")[1];
                System.out.println("Nombre del archivo: " + nombreArchivo);
                return nombreArchivo;
            }
        }
        return null;
    }

    private Map<String, String> parseParameters(String params) {
        Map<String, String> paramMap = new HashMap<>();
        String[] pairs = params.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length > 1) {
                paramMap.put(keyValue[0], keyValue[1]);
            }
        }
        return paramMap;
    }
}
