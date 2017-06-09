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

package io.fabric8.elasticsearch.plugin;

import static org.junit.Assert.fail;

import java.io.File;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.Before;
import org.junit.Test;

public class HttpsProxyClientCertAuthenticatorIntegrationTest {

    private final String keyStoreFile = System.getProperty("es.keystore");
    private final String keystorePW = System.getProperty("es.keystore_pw");
    private final String token = System.getProperty("auth.token");
    private final String proxyUser = System.getProperty("proxy.user");

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testProxyAuthWithSSL() throws Exception {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(new File(keyStoreFile), keystorePW.toCharArray(), new TrustSelfSignedStrategy())
                .build();

        try (CloseableHttpClient httpclient = HttpClients.custom().setSSLContext(sslContext)
                .setSSLHostnameVerifier(new HostnameVerifier() {

                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }

                }).build()) {
            Executor ex = Executor.newInstance(httpclient);
            Response response = ex.execute(Request.Get("https://localhost:9200/blahobar.234324234/logs/1")
                    .addHeader("Authorization", String.format("Bearer %s", token))
                    .addHeader("X-Proxy-Remote-User", proxyUser));
            System.out.println(response.returnContent().asString());
        } catch (Exception e) {
            System.out.println(e);
            fail("Test Failed");
        }
    }
}
