package br.edu.ifpr.yuru.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import br.edu.ifpr.yuru.core.QueryParameter;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Utils {

    public static Map<String, String> parseRequestLine(BufferedReader reader) throws IOException {
        final var result = new HashMap<String, String>();
        final var regex = "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|TRACE|CONNECT) (\\S+) (\\S+)";
        final var matcher = Pattern.compile(regex).matcher(reader.readLine());
        if (matcher.find()) {
            result.put("Method", matcher.group(1));
            result.put("Uri", matcher.group(2));
            result.put("Path", matcher.group(2).split("\\?")[0]);
            result.put("Version", matcher.group(3));
        }
        return result;
    }

    public static Map<String, String> parseRequestHeaders(BufferedReader reader) throws IOException {
        final var pattern = Pattern.compile("^(.*?):\\S*(.*)$");
        final var headers = new HashMap<String, String>();
        for (var line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
            final var matcher = pattern.matcher(line);
            if (matcher.find()) {
                headers.put(matcher.group(1), matcher.group(2).strip());
            }
        }
        return headers;
    }

    public static Map<String, String> parsePathParams(String[] uri, String[] pattern) {
        Map<String, String> pathParams = new HashMap<>();
        for (var index = 0; index != pattern.length; index += 1) {
            if (pattern[index].startsWith("{") && pattern[index].endsWith("}")) {
                pathParams.put(pattern[index].substring(1, pattern[index].length() - 1), uri[index]);
            }
        }
        return pathParams;
    }

    public static Map<String, QueryParameter> parseQueryParams(String uri) {
        Map<String, QueryParameter> queryParams = new HashMap<>();
        final var pattern = Pattern.compile("([\\w,]+)([~!=]?=)([^&]*)");
        final var matcher = pattern.matcher(uri);
        while (matcher.find()) {
            for (var param : matcher.group(1).split(","))
                queryParams.put(param, new QueryParameter(param, matcher.group(2), matcher.group(3)));
        }
        return queryParams;
    }

    public static String objectAsJson(Object object) throws JsonProcessingException {
        return new ObjectMapper().registerModule(new JavaTimeModule()).configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true).writeValueAsString(object);
    }

    public static String objectAsPrettyJson(Object object) throws JsonProcessingException {
        return new JSONObject(objectAsJson(object)).toString(4);
    }

}
