package br.edu.ifpr.yuru;

import br.edu.ifpr.yuru.enums.HttpMethod;
import br.edu.ifpr.yuru.exceptions.UnmappedEndpoint;
import br.edu.ifpr.yuru.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jooq.lambda.Seq;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpRouter {

    private record RouterHandler(String uri, HttpMethod method, Class<?> type, Function<HttpRequest, ?> handler) {}

    final private Integer MAX_THREAD_POOL_SIZE = 16;
    final private ExecutorService executors = Executors.newFixedThreadPool(MAX_THREAD_POOL_SIZE);
    final private Map<String, Map<String, RouterHandler>> routes = new ConcurrentHashMap<>();

    public <R> void registerRouter(Class<R> routerType, String uri, HttpMethod method, Class<?> bodyType, BiFunction<R, HttpRequest, ?> callable) {
        final var handler = new HashMap<>(Map.of(method.toString(), new RouterHandler(uri, method, bodyType, (request) -> {
            try {
                return callable.apply(routerType.getDeclaredConstructor().newInstance(), request);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        })));

        if (!routes.containsKey(uri)) {
            routes.put(uri, handler);
        } else {
            routes.get(uri).putAll(handler);
        }
    }

    public <R> void registerRouter(Class<R> routerType, String uri, HttpMethod method, BiFunction<R, HttpRequest, ?> callable) {
        final var handler = new HashMap<>(Map.of(method.toString(), new RouterHandler(uri, method, null, (request) -> {
            try {
                return callable.apply(routerType.getDeclaredConstructor().newInstance(), request);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        })));

        if (!routes.containsKey(uri)) {
            routes.put(uri, handler);
        } else {
            routes.get(uri).putAll(handler);
        }
    }

    public void routeAll() {
        try (
            final var server = new ServerSocket(8000)
        ) {
            while (true) {
                final var client = server.accept();
                executors.execute(() -> route(client));
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private RouterHandler getRouterHandler(HttpRequest request) throws UnmappedEndpoint {
        return routes.keySet().stream()
            .filter(route -> {
                final var routePattern = route.split("/");
                final var uriPattern = request.getPath().split("/");
                if (routePattern.length != uriPattern.length) return false;
                return Seq.zip(Arrays.stream(routePattern), Arrays.stream(uriPattern)).allMatch((value) ->
                    value.v1().equals(value.v2()) || value.v1().startsWith("{") && value.v1().endsWith("}")
                );
            })
            .map(routes::get)
            .map(route -> route.get(request.getMethod()))
            .findFirst()
            .orElseThrow(UnmappedEndpoint::new);
    }

    private String handleHttpPost(HttpRequest request, BufferedReader reader) throws UnmappedEndpoint {
        final var response = new StringBuilder();
        response.append("HTTP/1.1 200 OK\n");
        response.append("Accept-Encoding: UTF-8\n");
        response.append("Content-Length: 0\n");
        response.append("Access-Control-Allow-Origin: *\n");
        response.append("\n");

        final var router = getRouterHandler(request);
        if (router.uri().contains("{") && router.uri().contains("}")) {
            request.setPathParams(Utils.parsePathParams(request.getPath().split("/"), router.uri().split("/")));
        }

        try {
            final var mapper = new ObjectMapper().registerModule(new JavaTimeModule()).configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            final var bufferSize = Integer.parseInt(request.getHeader("Content-Length"));
            final var buffer = CharBuffer.allocate(bufferSize);
            if (reader.read(buffer) != -1) {
                request.setBody(mapper.readValue(String.copyValueOf(buffer.array()), router.type()));
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        router.handler().apply(request);
        return response.toString();
    }

    private String handleHttpPatch(HttpRequest request, BufferedReader reader) throws UnmappedEndpoint {
        final var response = new StringBuilder();
        response.append("HTTP/1.1 204 No Content\n");
        response.append("Accept-Encoding: UTF-8\n");
        response.append("Access-Control-Allow-Origin: *\n");
        response.append("\n");

        final var router = getRouterHandler(request);
        if (router.uri().contains("{") && router.uri().contains("}")) {
            request.setPathParams(Utils.parsePathParams(request.getPath().split("/"), router.uri().split("/")));
        }

        try {
            final var mapper = new ObjectMapper().registerModule(new JavaTimeModule()).configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            final var bufferSize = Integer.parseInt(request.getHeader("Content-Length"));
            final var buffer = CharBuffer.allocate(bufferSize);
            if (reader.read(buffer) != -1) {
                request.setBody(mapper.readValue(String.copyValueOf(buffer.array()), router.type()));
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        router.handler().apply(request);
        return response.toString();
    }

    private String handleHttpGet(HttpRequest request) throws UnmappedEndpoint {
        final var response = new StringBuilder();
        response.append("HTTP/1.1 200 OK\n");
        response.append("Accept-Encoding: UTF-8\n");
        response.append("Access-Control-Allow-Origin: *\n");
        response.append("Content-Type: application/json\n");

        final var router = getRouterHandler(request);
        if (router.uri().contains("{") && router.uri().contains("}")) {
            request.setPathParams(Utils.parsePathParams(request.getPath().split("/"), router.uri().split("/")));
        }

        final var result = router.handler().apply(request);

        try {
            if (result != null) {
                final var json = Utils.objectAsJson(result);
                response.append("Content-Length: ").append(json.getBytes().length).append("\n");
                response.append("\n");
                response.append(json);
            } else {
                response.append("Content-Length: 0\n");
                response.append("\n");
            }
        } catch (JsonProcessingException e) {
            System.out.println(e.getMessage());
        }

        return response.toString();
    }

    private String handleHttpDelete(HttpRequest request) throws UnmappedEndpoint {
        final var response = new StringBuilder();
        response.append("HTTP/1.1 204 No Content\n");
        response.append("Accept-Encoding: UTF-8\n");
        response.append("Access-Control-Allow-Origin: *\n");
        response.append("\n");

        final var router = getRouterHandler(request);
        if (router.uri().contains("{") && router.uri().contains("}")) {
            request.setPathParams(Utils.parsePathParams(request.getPath().split("/"), router.uri().split("/")));
        }

        router.handler().apply(request);
        return response.toString();
    }

    private static String handleHttpOptions(HttpRequest request) {
        final var response = new StringBuilder();
        response.append("HTTP/1.1 204 No Content\n");
        response.append("Access-Control-Allow-Origin: *\n");
        response.append("Access-Control-Allow-Methods: ").append(request.getHeader("Access-Control-Request-Method")).append("\n");
        response.append("Access-Control-Allow-Headers: ").append(request.getHeader("Access-Control-Request-Headers")).append("\n");
        return response.toString();
    }

    private static String handleHttpException(HttpRequest request, Exception exception) {
        final var response = new StringBuilder();
        response.append("HTTP/1.1 500 Internal Server Error\n");
        response.append("Access-Control-Allow-Origin: *\n");
        response.append("Accept-Encoding: UTF-8\n");
        response.append("Content-Type: application/json\n");
        final var content =
            "{" +
                "\"error\": {" +
                    "\"message\": " + String.format("\"%s\"", exception.getMessage().replace("\"", "\\\"")) + "," +
                    "\"request\": {" +
                        "\"path\": " + String.format("\"%s\"", request.getPath()) + "," +
                        "\"method\": " + String.format("\"%s\"", request.getMethod()) +
                    "}" +
                "}" +
            "}";
        response.append("Content-Length: ").append(content.length()).append("\n");
        response.append("\n");
        response.append(content);
        return response.toString();
    }

    private void route(Socket client) {
        try (final var writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
             final var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
        ) {
            final var request = new HttpRequest();
            request.setRequestLine(Utils.parseRequestLine(reader));
            request.setHeaders(Utils.parseRequestHeaders(reader));
            request.setQueryParams(Utils.parseQueryParams(request.getUri()));
            try {
                switch (request.getMethod()) {
                    case "OPTIONS" -> writer.println(handleHttpOptions(request));
                    case "DELETE" -> writer.println(handleHttpDelete(request));
                    case "GET" -> writer.println(handleHttpGet(request));
                    case "POST" -> writer.println(handleHttpPost(request, reader));
                    case "PATCH" -> writer.println(handleHttpPatch(request, reader));
                    default -> throw new RuntimeException("Unhandled request method: " + request.getMethod());
                }
            } catch (Exception exception) {
                writer.println(handleHttpException(request, exception));
            }
            System.out.printf(Thread.currentThread() + "[" + LocalDateTime.now() + "]: %s%n%n", Utils.objectAsPrettyJson(request));
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }
}