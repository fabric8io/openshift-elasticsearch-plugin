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

package io.fabric8.elasticsearch.integrationtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.PluginAwareNode;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.floragunn.searchguard.support.ConfigConstants;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenShiftElasticSearchPlugin;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.api.model.ProjectListBuilder;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Response.Builder;
import okhttp3.ResponseBody;

public abstract class ElasticsearchIntegrationTest {

    private static final String CLUSTER_NAME = "openshift_elastic_test_cluster";
    private static final String USERNAME = "username";
    private static final String RESPONSE = "response";
    private static final String URI = "uri";

    @Rule
    public TestName name = new TestName();
    @Rule
    public OpenShiftServer apiServer = new OpenShiftServer();

    protected Node esNode1;
    private String httpHost = null;
    private int httpPort = -1;
    protected Set<InetSocketTransportAddress> httpAdresses = new HashSet<InetSocketTransportAddress>();
    protected String nodeHost;
    protected int nodePort;
    protected boolean enableHttpClientSSL = false;
    protected boolean enableHttpClientSSLv3Only = false;
    protected boolean sendHttpClientCertificate = false;
    protected boolean trustHttpServerCertificate = false;
    private static String appFile;
    private static String basedir;
    private static String keystore;
    private static String truststore;
    private static String password = "changeit";
    private final Map<String, Object> testContext = new HashMap<>();

    protected final ESLogger log = Loggers.getLogger(this.getClass());

    @BeforeClass
    public static void setupOnce() throws Exception {
        basedir = System.getenv("PROJECT_DIR");

        appFile = basedir + "/src/test/resources/index-pattern.json";
        keystore = basedir + "/src/test/resources/keystore.jks";
        truststore = basedir + "/src/test/resources/keystore.jks";
    }


    @Rule
    public final TestWatcher testWatcher = new TestWatcher() {
        @Override
        protected void starting(final Description description) {
            final String methodName = description.getMethodName();
            String className = description.getClassName();
            className = className.substring(className.lastIndexOf('.') + 1);
            System.out.println(
                    "---------------- Starting JUnit-test: " + className + " " + methodName + " ----------------");
        }

        @Override
        protected void failed(final Throwable e, final Description description) {
            final String methodName = description.getMethodName();
            String className = description.getClassName();
            className = className.substring(className.lastIndexOf('.') + 1);
            System.out.println(">>>> " + className + " " + methodName + " FAILED due to " + e);
        }

        @Override
        protected void finished(final Description description) {
            // System.out.println("-----------------------------------------------------------------------------------------");
        }

    };

    public ElasticsearchIntegrationTest() {
        super();
    }

    protected void seedSearchGuardAcls() throws Exception {
        log.info("Starting seeding of SearchGuard ACLs...");
        String configdir = basedir + "/src/test/resources/sgconfig";
        String[] cmd = { basedir + "/tools/sgadmin.sh", "-cd", configdir, "-ks", keystore, "-kst", "JKS", "-kspass",
            password, "-ts", truststore, "-tst", "JKS", "-tspass", password, "-nhnv", "-nrhn", "-icl" };
        String[] envvars = { "CONFIG_DIR=" + configdir, "SCRIPT_CP=" + System.getProperty("surefire.test.class.path") };
        log.debug("Seeding ACLS with: {}, {}", cmd, envvars);
        Runtime rt = Runtime.getRuntime();
        Process process = rt.exec(cmd, envvars);
        if (0 != process.waitFor()) {
            log.error("Stdout of seeding SearchGuard ACLs:\n{}", IOUtils.toString(process.getInputStream()));
            fail("Error seeding SearchGuard ACLs:\n{}" + IOUtils.toString(process.getErrorStream()));
        } else {
            log.debug("Stdout of seeding SearchGuard ACLs:\n{}", IOUtils.toString(process.getInputStream()));
        }
        log.info("Completed seeding SearchGuard ACL");
    }

    protected Settings nodeSettings() {
        Settings settings = Settings.builder()
                // .put("path.conf", this.getDataPath("/config"))
                // set to false to completely disable Searchguard plugin functionality, this
                // should result into failed tests?
                .put("searchguard.enabled", true)
                // Disabling ssl should fail, though it seems to be overridden somewhere...
                // .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, false)
                // .put("searchguard.ssl.http.enabled", false)
                .put("http.port", 9200).put("transport.tcp.port", 9300).put("cluster.name", CLUSTER_NAME)
                .put("network.host", "_local_").putArray("searchguard.nodes_dn", "CN=*")
                .putArray("searchguard.authcz.admin_dn", "CN=*").put(ConfigurationSettings.SG_CLIENT_KS_PATH, keystore)
                .put(ConfigurationSettings.SG_CLIENT_TS_PATH, truststore)
                .put("searchguard.ssl.transport.keystore_type", "JKS")
                .put("searchguard.ssl.transport.keystore_password", password)
                .put("searchguard.ssl.transport.keystore_filepath", keystore)
                .put("searchguard.ssl.transport.trustore_type", "JKS")
                .put("searchguard.ssl.transport.trustore_password", password)
                .put("searchguard.ssl.transport.truststore_filepath", truststore)
                .put("searchguard.ssl.http.keystore_type", "JKS")
                .put("searchguard.ssl.http.keystore_password", password)
                .put("searchguard.ssl.http.keystore_filepath", keystore)
                .put("searchguard.ssl.http.trustore_type", "JKS")
                .put("searchguard.ssl.http.trustore_password", password)
                .put("searchguard.ssl.http.truststore_filepath", truststore)
                .put("discovery.zen.ping.multicast.enabled", false)
                .put("searchguard.ssl.http.enable_openssl_if_available", false)
                .put("searchguard.ssl.transport.enable_openssl_if_available", false)
                .put("searchguard.ssl.transport.enforce_hostname_verification", false)
                .put(ConfigurationSettings.OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_APP,
                        basedir + "/src/test/resources/index-pattern.json")
                .put(ConfigurationSettings.OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_OPERATIONS,
                        basedir + "/src/test/resources/index-pattern.json")
                .put(ConfigurationSettings.OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_EMPTY,
                        basedir + "/src/test/resources/index-pattern.json")
                .build();
        return settings;
    }

    private Settings.Builder getDefaultSettingsBuilder(final int nodenum, final boolean dataNode,
            final boolean masterNode) throws Exception {
        String tmp = Files.createTempDirectory(null).toAbsolutePath().toString();
        log.info("Using base directory: {}", tmp);
        return Settings.settingsBuilder().put("node.name", "openshift_test_" + nodenum).put("node.data", dataNode)
                .put("node.master", masterNode).put("cluster.name", CLUSTER_NAME).put("path.data", tmp + "/data/data")
                .put("path.work", tmp + "/data/work").put("path.logs", tmp + "/data/logs")
                .put("path.conf", tmp + "/data/config").put("path.plugins", tmp + "/data/plugins")
                .put("index.number_of_shards", "1").put("index.number_of_replicas", "0").put("http.enabled", true)
                .put("cluster.routing.allocation.disk.watermark.high", "1mb")
                .put("cluster.routing.allocation.disk.watermark.low", "1mb").put("http.cors.enabled", true)
                .put("node.local", false).put("discovery.zen.minimum_master_nodes", 1).put("path.home", tmp.toString());
    }

    protected final String getHttpServerUri() {
        final String address = "http" + (enableHttpClientSSL ? "s" : "") + "://" + httpHost + ":" + httpPort;
        log.debug("Connect to {}", address);
        return address;
    }

    public final void startES() throws Exception {
        startES(nodeSettings(), 30, 1);
    }

    public final void startES(final Settings settings, int timeOutSec, int assertNodes) throws Exception {
        // setup api server
        final String masterUrl = apiServer.getMockServer().url("/").toString();
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, masterUrl);
        System.setProperty("kubernetes.trust.certificates", "true");
        System.setProperty("kubernetes.keystore.file", keystore);
        System.setProperty("kubernetes.keystore.passphrase", password);
        System.setProperty("kubernetes.truststore.file", truststore);
        System.setProperty("kubernetes.truststore.passphrase", password);

        FileUtils.deleteDirectory(new File("data"));

        esNode1 = new PluginAwareNode(
                getDefaultSettingsBuilder(1, true, true)
                        .put(settings == null ? Settings.Builder.EMPTY_SETTINGS : settings).build(),
                OpenShiftElasticSearchPlugin.class);

        esNode1.start();

        waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(timeOutSec), esNode1.client(),
                assertNodes);

        // seed kibana index like kibana
        XContentBuilder content = XContentFactory.jsonBuilder().startObject().field("key", "value").endObject();
        givenDocumentIsIndexed(".kibana", "config", "0", content);
        seedSearchGuardAcls();

        // create ops user to avoid issue:
        // https://github.com/fabric8io/openshift-elasticsearch-plugin/issues/106
        givenDocumentIsIndexed(".operations.1970.01.01", "data", "0", content);
    }

    protected void givenDocumentIsIndexed(String index, String type, String id, XContentBuilder content)
            throws Exception {
        esNode1.client().prepareIndex(index, type, id).putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true")
                .setSource(content).execute().get();
    }

    protected void givenUserIsClusterAdmin(String user) {
        expSubjectAcccessReviewToBe(Boolean.TRUE, user);
    }

    protected void givenUserIsNotClusterAdmin(String user) {
        expSubjectAcccessReviewToBe(Boolean.FALSE, user);
    }

    private void expSubjectAcccessReviewToBe(boolean value, String user) {
        testContext.put(USERNAME, user);
        SubjectAccessReviewResponse response = new SubjectAccessReviewResponse(Boolean.FALSE, "v1", null, null, null,
                "");
        apiServer.expect().post().withPath("/oapi/v1/subjectaccessreviews").andReturn(200, response).once();
        
        apiServer.expect().get().withPath("/oapi/v1/users/~").andReturn(200, "{ \"metadata\": { \"name\": \"" + user.replace("\\", "\\\\") + "\" } }").once();
    }

    protected void givenUserIsAdminForProjects(String... projects) throws Exception {
        ProjectListBuilder builder = new ProjectListBuilder(false);
        for (String project : projects) {
            builder.addToItems(new ProjectBuilder(false).withNewMetadata().withName(project).endMetadata().build());
        }
        apiServer.expect().withPath("/oapi/v1/projects").andReturn(200, builder.build()).once();
    }

    protected void whenGettingDocument(String uri) throws Exception {
        testContext.put(URI, uri);
        RequestRunner runner = new RequestRunnerBuilder().username((String) testContext.get(USERNAME)).build();
        testContext.put(RESPONSE, runner.run(uri));
    }

    protected void whenCheckingIndexExists(String uri) throws Exception {
        testContext.put(URI, uri);
        RequestRunner runner = new RequestRunnerBuilder().username((String) testContext.get(USERNAME)).method("head")
                .build();
        testContext.put(RESPONSE, runner.run(uri));
    }

    protected void assertThatResponseIsSuccessful() {
        String username = (String) testContext.get(USERNAME);
        HttpResponse response = (HttpResponse) testContext.get(RESPONSE);
        String uri = (String) testContext.get(URI);
        assertEquals(String.format("Exp. %s request to succeed for %s", username, uri), 200, response.getStatusCode());
    }

    protected void assertThatResponseIsForbidden() {
        String username = (String) testContext.get(USERNAME);
        HttpResponse response = (HttpResponse) testContext.get(RESPONSE);
        String uri = (String) testContext.get(URI);
        assertEquals(String.format("Exp. %s to be unauthorized for %s", username, uri), 403, response.getStatusCode());
    }

    protected Client client() {
        return esNode1.client();
    }

    @Before
    public void setUp() throws Exception {
        enableHttpClientSSL = false;
        enableHttpClientSSLv3Only = false;
    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(500);

        if (esNode1 != null) {
            esNode1.close();
        }
    }

    protected void waitForGreenClusterState(final Client client) throws IOException {
        waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(30), client, 3);
    }

    protected void waitForCluster(final ClusterHealthStatus status, final TimeValue timeout, final Client client,
            int assertNodes) throws IOException {
        try {
            log.debug("waiting for cluster state {}", status.name());
            final ClusterHealthResponse healthResponse = client.admin().cluster().prepareHealth()
                    .setWaitForStatus(status).setTimeout(timeout).setWaitForNodes(String.valueOf(assertNodes)).execute()
                    .actionGet();
            if (healthResponse.isTimedOut()) {
                throw new IOException("cluster state is " + healthResponse.getStatus().name() + " with "
                        + healthResponse.getNumberOfNodes() + " nodes");
            } else {
                log.debug("... cluster state ok " + healthResponse.getStatus().name() + " with "
                        + healthResponse.getNumberOfNodes() + " nodes");
            }

            org.junit.Assert.assertEquals(assertNodes, healthResponse.getNumberOfNodes());

            final NodesInfoResponse res = esNode1.client().admin().cluster().nodesInfo(new NodesInfoRequest())
                    .actionGet();

            final NodeInfo[] nodes = res.getNodes();

            for (int i = 0; i < nodes.length; i++) {
                final NodeInfo nodeInfo = nodes[i];
                if (nodeInfo.getHttp() != null && nodeInfo.getHttp().address() != null) {
                    final InetSocketTransportAddress is = (InetSocketTransportAddress) nodeInfo.getHttp().address()
                            .publishAddress();
                    httpPort = is.getPort();
                    httpHost = is.getHost();
                    httpAdresses.add(is);
                }

                final InetSocketTransportAddress is = (InetSocketTransportAddress) nodeInfo.getTransport().getAddress()
                        .publishAddress();
                nodePort = is.getPort();
                nodeHost = is.getHost();
            }
        } catch (final ElasticsearchTimeoutException e) {
            throw new IOException(
                    "timeout, cluster does not respond to health request, cowardly refusing to continue with operations");
        }
    }

    public File getAbsoluteFilePathFromClassPath(final String fileNameFromClasspath) {
        File file = null;
        final URL fileUrl = ElasticsearchIntegrationTest.class.getClassLoader().getResource(fileNameFromClasspath);
        if (fileUrl != null) {
            try {
                file = new File(URLDecoder.decode(fileUrl.getFile(), "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                return null;
            }

            if (file.exists() && file.canRead()) {
                return file;
            } else {
                log.error("Cannot read from {}, maybe the file does not exists? ", file.getAbsolutePath());
            }

        } else {
            log.error("Failed to load " + fileNameFromClasspath);
        }
        return null;
    }

    protected String executeSimpleRequest(final String user, final String request) throws Exception {

        HttpGet getRequest = new HttpGet(getHttpServerUri() + "/" + request);
        getRequest.addHeader("X-Proxy-Remote-User", user);
        getRequest.addHeader("X-Forwarded-For", "127.0.0.1");
        getRequest.addHeader("Authorization", "Bearer testtoken");
        
        return executeSimpleRequest(getRequest);
    }
    
    protected String executeSimpleRequest(final String request) throws Exception {

        HttpGet getRequest = new HttpGet(getHttpServerUri() + "/" + request);
        return executeSimpleRequest(getRequest);
    }
    
    private String executeSimpleRequest(final HttpGet getRequest) throws Exception {
        
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse response = null;
        try {
            httpClient = getHttpClient();

            response = httpClient.execute(getRequest);

            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new Exception("Statuscode " + response.getStatusLine().getStatusCode());
            }

            return IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8.toString());
        } finally {

            if (response != null) {
                response.close();
            }

            if (httpClient != null) {
                httpClient.close();
            }
        }
    }

    protected class HttpResponse {
        private final CloseableHttpResponse inner;
        private final String body;
        private final Header[] header;
        private final int statusCode;
        private final String statusReason;

        public HttpResponse(CloseableHttpResponse inner) throws IllegalStateException, IOException {
            super();
            this.inner = inner;
            this.body = inner.getEntity() == null ? null
                    : IOUtils.toString(inner.getEntity().getContent(), StandardCharsets.UTF_8.toString());
            this.header = inner.getAllHeaders();
            this.statusCode = inner.getStatusLine().getStatusCode();
            this.statusReason = inner.getStatusLine().getReasonPhrase();
            inner.close();
        }

        public CloseableHttpResponse getInner() {
            return inner;
        }

        public String getBody() {
            return body;
        }

        public Header[] getHeader() {
            return header;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusReason() {
            return statusReason;
        }

    }

    protected HttpResponse executeGetRequest(final String request, Header... header) throws Exception {
        return executeRequest(new HttpGet(getHttpServerUri() + "/" + request), header);
    }

    protected HttpResponse executeHeadRequest(final String request, Header... header) throws Exception {
        return executeRequest(new HttpHead(getHttpServerUri() + "/" + request), header);
    }

    protected HttpResponse executePutRequest(final String request, String body, Header... header) throws Exception {
        HttpPut uriRequest = new HttpPut(getHttpServerUri() + "/" + request);
        if (!Strings.isNullOrEmpty(body)) {
            uriRequest.setEntity(new StringEntity(body));
        }

        return executeRequest(uriRequest, header);

    }

    protected HttpResponse executePostRequest(final String request, String body, Header... header) throws Exception {
        HttpPost uriRequest = new HttpPost(getHttpServerUri() + "/" + request);
        if (!Strings.isNullOrEmpty(body)) {
            uriRequest.setEntity(new StringEntity(body));
        }

        return executeRequest(uriRequest, header);
    }

    protected HttpResponse executeDeleteRequest(final String request, Header... header) throws Exception {
        return executeRequest(new HttpDelete(getHttpServerUri() + "/" + request), header);
    }

    protected HttpResponse executeRequest(HttpUriRequest uriRequest, Header... header) throws Exception {

        CloseableHttpClient httpClient = null;
        try {

            httpClient = getHttpClient();

            if (header != null && header.length > 0) {
                for (int i = 0; i < header.length; i++) {
                    Header h = header[i];
                    uriRequest.addHeader(h);
                }
            }

            HttpResponse res = new HttpResponse(httpClient.execute(uriRequest));
            log.trace(res.getBody());
            return res;
        } finally {

            if (httpClient != null) {
                httpClient.close();
            }
        }
    }

    protected final CloseableHttpClient getHttpClient() throws Exception {

        final HttpClientBuilder hcb = HttpClients.custom();

        if (enableHttpClientSSL) {

            log.debug("Configure HTTP client with SSL");

            final KeyStore myTrustStore = KeyStore.getInstance("JKS");
            myTrustStore.load(new FileInputStream(truststore), password.toCharArray());

            final KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(keystore), password.toCharArray());

            final SSLContextBuilder sslContextbBuilder = SSLContexts.custom().useTLS();

            if (trustHttpServerCertificate) {
                sslContextbBuilder.loadTrustMaterial(myTrustStore);
            }

            if (sendHttpClientCertificate) {
                sslContextbBuilder.loadKeyMaterial(keyStore, "changeit".toCharArray());
            }

            final SSLContext sslContext = sslContextbBuilder.build();

            String[] protocols = null;

            if (enableHttpClientSSLv3Only) {
                protocols = new String[] { "SSLv3" };
            } else {
                protocols = new String[] { "TLSv1", "TLSv1.1", "TLSv1.2" };
            }

            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, protocols, null,
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            hcb.setSSLSocketFactory(sslsf);
        }

        hcb.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(60 * 1000).build());

        return hcb.build();
    }

    protected final String loadFile(final String file) throws IOException {
        final StringWriter sw = new StringWriter();
        IOUtils.copy(this.getClass().getResourceAsStream("/" + file), sw, StandardCharsets.UTF_8.toString());
        return sw.toString();
    }

    protected BytesReference readYamlContent(final String file) {
        try {
            return readXContent(new StringReader(loadFile(file)), XContentType.YAML);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected BytesReference readXContent(final Reader reader, final XContentType contentType) throws IOException {
        XContentParser parser = null;
        try {
            parser = XContentFactory.xContent(contentType).createParser(reader);
            parser.nextToken();
            final XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.copyCurrentStructure(parser);
            return builder.bytes();
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    public static String encodeBasicHeader(final String username, final String password) {
        return new String(DatatypeConverter.printBase64Binary(
                (username + ":" + Objects.requireNonNull(password)).getBytes(StandardCharsets.UTF_8)));
    }

    class RequestRunner {

        private String username;
        private String token;
        private Map<String, String> headers;
        private String method;
        private String body;

        RequestRunner(String method, String username, String token, Map<String, String> headers, String body) {
            this.username = username;
            this.token = token;
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
        HttpResponse run(final String query) throws Exception {
            Collection<Header> headers = new ArrayList<>(this.headers.size());
            headers.add(new BasicHeader("x-proxy-remote-user", this.username));
            headers.add(new BasicHeader(ConfigurationSettings.DEFAULT_AUTH_PROXY_HEADER, "Bearer " + this.token));
            for (Map.Entry<String, String> pair : this.headers.entrySet()) {
                headers.add(new BasicHeader(pair.getKey(), pair.getValue()));
            }
            switch (StringUtils.defaultIfBlank(method, "get").toLowerCase()) {
            case "head":
                return executeHeadRequest(query, headers.toArray(new Header[headers.size()]));
            case "put":
                return executePutRequest(query, body, headers.toArray(new Header[headers.size()]));
            case "delete":
                return executeDeleteRequest(query, headers.toArray(new Header[headers.size()]));
            case "post":
                return executePostRequest(query, body, headers.toArray(new Header[headers.size()]));
            default:
                return executeGetRequest(query, headers.toArray(new Header[headers.size()]));
            }
        }
    }

    RequestRunnerBuilder newRequestRunnerBuilder() {
        return new RequestRunnerBuilder();
    }

    class RequestRunnerBuilder {
        private String token = "developeroauthtoken";
        private String username = "developer";
        private Map<String, String> headers = new HashMap<>();
        private String method;
        private String body;

        RequestRunnerBuilder() {
            headers.put("X-Forwarded-By", "127.0.0.1");
            headers.put("x-forwarded-for", "127.0.0.1");
            
            headers.put("X-Proxy-Remote-User", username);
            headers.put("Authorization", "Bearer " + token);
        }

        RequestRunnerBuilder method(String method) {
            this.method = method;
            return this;
        }

        RequestRunnerBuilder body(String body) {
            this.body = body;
            return this;
        }

        RequestRunnerBuilder header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        RequestRunnerBuilder token(String token) {
            this.token = token;
            return this;
        }

        RequestRunnerBuilder username(String username) {
            this.username = username;
            return this;
        }

        RequestRunner build() throws Exception {

            return new RequestRunner(method, username, token, headers, body);
        }
    }

    protected String formatUserName(String username) {
        if (username.contains("\\")) {
            return username.replace("\\", "/");
        }
        return username;
    }
}
