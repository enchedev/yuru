package br.edu.ifpr.yuru;

import br.edu.ifpr.yuru.core.QueryParameter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class HttpRequest {

    @JsonIgnore
    private Map<String, String> requestLine;
    private Map<String, String> headers;
    private Map<String, String> pathParams;
    private Map<String, QueryParameter> queryParams;
    private Object body;

    public String getMethod() { return requestLine.get("Method"); }
    public void setMethod(String method) { this.requestLine.put("Method", method); }

    public String getUri() { return requestLine.get("Uri"); }
    public void getUri(String uri) { this.requestLine.put("Uri", uri); }

    public String getPath() { return requestLine.get("Path"); }
    public void getPath(String path) { this.requestLine.put("Path", path); }

    public String getVersion() { return requestLine.get("Version"); }
    public void setVersion(String version) { this.requestLine.put("Version", version); }

    public String getHeader(String key) { return headers.get(key); }
    public void setHeader(String key, String value) { headers.put(key, value); }

    public String getPathParam(String key) { return pathParams.get(key); }
    public void setPathParam(String key, String value) { pathParams.put(key, value); }

    public QueryParameter getQueryParam(String key) { return queryParams.get(key); }
    public void setQueryParam(String key, QueryParameter value) { queryParams.put(key, value); }

}
