/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.elasticsearch;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import io.fabric8.elasticsearch.util.RequestUtils;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

public class RequestRunner {
    
    private static final Logger log = Loggers.getLogger(RequestRunner.class);
    private String username;
    private Map<String, String> headers;
    private String method;
    private String body;
    private String password;
    private String keyStore;
    private String server;

    public RequestRunner(String server, String keystore, String keystorePswd, String method, String username, Map<String, String> headers, String body) {
        this.server = server;
        this.keyStore = keystore;
        this.password = keystorePswd;
        this.username = username;
        this.headers = headers;
        this.method = method;
        this.body = body;
    }

    /**
     * Run the request
     * 
     * @param query
     *            The URI with protocol:host:port
     * @return return the request that was run
     * @throws Exception
     *             the exception if there is an error
     */
    public Response run(final String query) throws Exception {
        Headers.Builder builder = new Headers.Builder()
                .add("connection","close")
                .add("x-proxy-remote-user", this.username)
                .add("x-forwarded-for", "127.0.0.1")
                .add(RequestUtils.AUTHORIZATION_HEADER, "Bearer " + username + "-token");
        for (Map.Entry<String, String> pair : this.headers.entrySet()) {
            builder.add(pair.getKey(), pair.getValue());
        }
        return run(query, builder.build());
    }
    
    public Response run(final String query,  Headers headers) throws Exception {
        switch (StringUtils.defaultIfBlank(method, "get").toLowerCase()) {
        case "head":
            return executeHeadRequest(query, headers);
        case "put":
            return executePutRequest(query, body, headers);
        case "delete":
            return executeDeleteRequest(query, headers);
        case "post":
            return executePostRequest(query, body, headers);
        default:
            return executeGetRequest(query, headers);
        }
    }

    protected Response executeGetRequest(final String uri, Headers headers) throws Exception {
        return executeRequest(uri, headers, "GET", null);
    }

    protected Response executeHeadRequest(final String uri, Headers headers) throws Exception {
        return executeRequest(uri, headers, "HEAD", null);
    }

    protected Response executePutRequest(final String uri, String body, Headers headers) throws Exception {
        RequestBody requestBody = RequestBody.create(null, body);
        return executeRequest(uri, headers, "PUT", requestBody);
    }

    protected Response executePostRequest(final String uri, String body, Headers headers) throws Exception {
        RequestBody requestBody = RequestBody.create(null, body);
        return executeRequest(uri, headers, "POST", requestBody);
    }

    protected Response executeDeleteRequest(final String uri, Headers headers) throws Exception {
        return executeRequest(uri, headers, "DELETE", null);
    }

    protected Response executeRequest(final String uri, Headers headers, String method, RequestBody body) throws Exception {
        OkHttpClient client = getHttpClient();
        Request.Builder builder = new Request.Builder();
        okhttp3.Request request = builder.headers(headers)
            .url(server + "/" + uri)
            .method(method, body)
            .build();
        if(log.isTraceEnabled()) {
            log.info(request);
            if (body != null) {
                Buffer sink = new Buffer();
                body.writeTo(sink);
                log.info(IOUtils.toString(sink.inputStream()));
            }
        }
        Response response = client.newCall(request).execute();
        if(log.isTraceEnabled()) {
            log.trace(response);
            if(response.body() != null) {
                log.trace(IOUtils.toString(response.body().byteStream()));
            }
        }
        return response;
    }

    protected final OkHttpClient getHttpClient() throws Exception {
        File ksFile = new File(keyStore);
        KeyStore trusted = KeyStore.getInstance("JKS");
        FileInputStream in = new FileInputStream(ksFile);
        trusted.load(in, password.toCharArray());
        in.close();
        SSLContext sslContext = SSLContext.getInstance("TLS");
        TrustManagerFactory trustManagerFactory = InsecureTrustManagerFactory.INSTANCE;
        X509TrustManager trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .readTimeout(1, TimeUnit.MINUTES)
                .writeTimeout(1, TimeUnit.MINUTES)
                .build();
        return client;
    }
    
    static class Builder {
        private String username;
        private Map<String, String> headers = new HashMap<>();
        private String method;
        private String body;
        private String keyStore;
        private String keyStorePswd;
        private String server;
        
        Builder() {
            headers.put("X-Forwarded-By", "127.0.0.1");
            headers.put("x-forwarded-for", "127.0.0.1");
            
        }
        
        Builder method(String method) {
            this.method = method;
            return this;
        }
        
        Builder body(String body) {
            this.body = body;
            return this;
        }
        
        Builder header(String key, String value) {
            headers.put(key, value);
            return this;
        }
        
        Builder username(String username) {
            this.username = username;
            return this;
        }
        
        Builder keyStore(String keyStore) {
            this.keyStore = keyStore;
            return this;
        }
        
        Builder keyStorePswd(String keyStorePswd) {
            this.keyStorePswd = keyStorePswd;
            return this;
        }
        
        Builder server(String server) {
            this.server = server;
            return this;
        }
        
        RequestRunner build() throws Exception {
            headers.put("X-Proxy-Remote-User", username);
            headers.put("Authorization", "Bearer " + username + "-token");

            return new RequestRunner(server, keyStore, keyStorePswd, method, username, headers, body);
        }
    }
}