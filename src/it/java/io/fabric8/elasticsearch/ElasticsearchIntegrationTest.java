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

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchTimeoutException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.util.concurrent.ThreadContext.StoredContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
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

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;
import com.floragunn.searchguard.support.ConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenShiftElasticSearchPlugin;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.api.model.ProjectListBuilder;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.client.server.mock.OpenShiftServer;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public abstract class ElasticsearchIntegrationTest {

    protected static final Logger log = Loggers.getLogger(ElasticsearchIntegrationTest.class);
    private static final String CLUSTER_NAME = "openshift_elastic_test_cluster";
    private static final String USERNAME = "username";
    protected static final String RESPONSE = "response";
    private static final String URI = "uri";
    private static String basedir;
    private static String password = "changeit";
    private static String keyStore;
    private static String trustStore;

    @Rule
    public TestName name = new TestName();
    @Rule
    public OpenShiftServer apiServer = new OpenShiftServer();

    protected Node esNode1;
    protected Set<InetSocketTransportAddress> httpAdresses = new HashSet<InetSocketTransportAddress>();
    protected String nodeHost;
    protected int nodePort;
    private String httpHost = null;
    private int httpPort = -1;
    protected Map<String, Object> testContext;

    @BeforeClass
    public static void setupOnce() throws Exception {
        basedir = System.getenv("PROJECT_DIR");

        keyStore = basedir + "/src/it/resources/keystore.jks";
        trustStore = basedir + "/src/it/resources/truststore.jks";
    }
    
    @Rule
    public final TestWatcher testWatcher = new TestWatcher() {
        @Override
        protected void starting(final Description description) {
            final String methodName = description.getMethodName();
            String className = description.getClassName();
            className = className.substring(className.lastIndexOf('.') + 1);
            log.info("---------------- Starting JUnit-test: {} {} ----------------", className, methodName);
        }

        @Override
        protected void failed(final Throwable e, final Description description) {
            final String methodName = description.getMethodName();
            String className = description.getClassName();
            className = className.substring(className.lastIndexOf('.') + 1);
            log.error(">>>> " + className + " " + methodName + " FAILED due to " + e);
        }

        @Override
        protected void finished(final Description description) {
        }

    };

    /*
     * Shamelessly pulled from SearchGuardAdmin
     */
    private boolean uploadFile(Client tc, String filepath, String index, String type) {
        log.info("Will update '" + type + "' with " + filepath);
        try (Reader reader = new FileReader(filepath)) {

            final String id = tc
                    .index(new IndexRequest(index).type(type).id("0").setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                            .source(type, readXContent(reader, XContentType.YAML))).actionGet().getId();

            if ("0".equals(id)) {
                log.info("   SUCC: Configuration for '" + type + "' created or updated");
                return true;
            } else {
                log.info("   FAIL: Configuration for '" + type
                        + "' failed for unknown reasons. Pls. consult logfile of elasticsearch");
            }
        } catch (Exception e) {
            log.info("   FAIL: Configuration for '" + type + "' failed because of " + e.toString());
        }

        return false;
    }

    protected String getKibanaIndex(String mode, String user, boolean isAdmin) {
        return OpenshiftRequestContextFactory.getKibanaIndex(".kibana",  mode, user, isAdmin);
    }
    
    protected void seedSearchGuardAcls() throws Exception {
        ThreadContext threadContext = esNode1.client().threadPool().getThreadContext();
        try (StoredContext cxt = threadContext.stashContext()) {
            log.info("Starting seeding of SearchGuard ACLs...");
            threadContext.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            Client client = esNode1.client();
            String configdir = basedir + "/src/test/resources/sgconfig";
            for (String config : new String [] {"actiongroups", "config", "internalusers", "rolesmapping", "roles"}) {
                uploadFile(client, String.format("%s/%s.yml", configdir,config), ".searchguard", config);
            }
            ((NodeClient)client()).execute(ConfigUpdateAction.INSTANCE, 
                    new ConfigUpdateRequest(new String[]{"config","roles","rolesmapping","internalusers","actiongroups"})).actionGet();
            log.info("Completed seeding SearchGuard ACL");
        }
    }
    
    /**
     * Provide additional settings or override the defaults
     * @return Settings
     */
    protected Settings additionalNodeSettings() {
        return Settings.EMPTY;
    }

    protected Settings nodeSettings() throws Exception {
        String tmp = Files.createTempDirectory(null).toAbsolutePath().toString();
        log.info("Using base directory: {}", tmp);
        Settings settings = Settings.builder()
                .put("path.home", tmp)
                .put("path.conf", basedir + "/src/test/resources")
                .put("http.port", 9200)
                .put("transport.tcp.port", 9300)
                .put("cluster.name", CLUSTER_NAME)
                .put("discovery.zen.minimum_master_nodes", 1)
                .put("node.data", true)
                .put("node.master", true)
                .put("gateway.expected_nodes", 1)
                .put("discovery.zen.minimum_master_nodes", 1)
                .putArray("searchguard.authcz.admin_dn", "CN=*")
                .putArray("searchguard.nodes_dn", "CN=*")
                .put(ConfigConstants.SG_CONFIG_INDEX, ".searchguard")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_ENABLED, true)
                .put(ConfigurationSettings.SG_CLIENT_KS_PATH, keyStore)
                .put(ConfigurationSettings.SG_CLIENT_TS_PATH, trustStore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_KEYSTORE_TYPE, "JKS")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_KEYSTORE_FILEPATH, keyStore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_KEYSTORE_PASSWORD, password)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_TRUSTSTORE_TYPE, "JKS")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_TRUSTSTORE_FILEPATH, trustStore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_HTTP_TRUSTSTORE_PASSWORD, password)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, true)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_TYPE, "JKS")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, keyStore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD, password)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_TYPE, "JKS")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, trustStore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_PASSWORD, password)
                .put("searchguard.ssl.transport.enable_openssl_if_available", false)
                .put("searchguard.ssl.transport.enforce_hostname_verification", false)
                .put("searchguard.ssl.transport.resolve_hostname", false)
                .put(ConfigurationSettings.OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_APP,
                        basedir + "/src/test/resources/index-pattern.json")
                .put(ConfigurationSettings.OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_OPERATIONS,
                        basedir + "/src/test/resources/index-pattern.json")
                .put(ConfigurationSettings.OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_EMPTY,
                        basedir + "/src/test/resources/index-pattern.json")
                .put("searchguard.ssl.http.enable_openssl_if_available", false)
                .put(additionalNodeSettings())
                .build();
        return settings;
    }

    protected final String getHttpServerUri() {
        final String address = "https://" + httpHost + ":" + httpPort;
        log.debug("Connect to {}", address);
        return address;
    }

    @After
    public void tearDown() throws Exception {
        Thread.sleep(500);
        if (esNode1 != null) {
            log.info("--------- Stopping ES Node ----------");
            esNode1.close();
        }
    }
    
    @Before
    public void startES() throws Exception {
        testContext = new HashMap<>();
        startES(nodeSettings(), 30, 1);
    }
    
    public final void startES(final Settings settings, int timeOutSec, int assertNodes) throws Exception {
        // setup api server
        final String masterUrl = apiServer.getMockServer().url("/").toString();
        System.setProperty(Config.KUBERNETES_MASTER_SYSTEM_PROPERTY, masterUrl);
        System.setProperty("kubernetes.trust.certificates", "true");
        System.setProperty("kubernetes.keystore.file", keyStore);
        System.setProperty("kubernetes.keystore.passphrase", password);
        System.setProperty("kubernetes.truststore.file", keyStore);
        System.setProperty("kubernetes.truststore.passphrase", password);
        System.setProperty("sg.display_lic_none", "true");

        FileUtils.deleteDirectory(new File("data"));

        esNode1 = new PluginAwareNode(
                settings,
                OpenShiftElasticSearchPlugin.class);
        log.debug("--------- Starting ES Node ----------");
        esNode1.start();
        log.debug("--------- Waiting for the cluster to go green ----------");
        waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(timeOutSec), esNode1.client(),
                assertNodes);

        seedSearchGuardAcls();
        // seed kibana index like kibana
        givenDocumentIsIndexed(".kibana", "config", "0", "defaultKibanaIndex");

        // create ops user to avoid issue:
        // https://github.com/fabric8io/openshift-elasticsearch-plugin/issues/106
        givenDocumentIsIndexed(".operations.1970.01.01", "test", "0", "operation0");
    }

    protected void givenDocumentIsIndexed(String index, String type, String id, String message) throws Exception{
        givenDocumentIsIndexed(index, type, id, createSimpleDocument(message));
    }

    protected void givenDocumentIsIndexed(String index, String type, String id, XContentBuilder content)
            throws Exception {
        ThreadContext threadContext = esNode1.client().threadPool().getThreadContext();
        try (StoredContext cxt = threadContext.stashContext()) {
            threadContext.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            esNode1.client().prepareIndex(index, type, id)
            .setSource(content).execute().get();
        }
    }
    
    protected void dumpIndices() throws Exception {
        ThreadContext threadContext = esNode1.client().threadPool().getThreadContext();
        try (StoredContext cxt = threadContext.stashContext()) {
            threadContext.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            ClusterStateResponse response = esNode1.client().admin().cluster().prepareState().get();
            Iterator<ObjectObjectCursor<String, IndexMetaData>> iterator = response.getState().getMetaData().indices().iterator();
            while (iterator.hasNext()) {
                ObjectObjectCursor<String, IndexMetaData> c = (ObjectObjectCursor<String, IndexMetaData>) iterator.next();
                IndexMetaData meta = c.value;
                ImmutableOpenMap<String, MappingMetaData> mappings = meta.getMappings();
                Iterator<String> it = mappings.keysIt();
                while (it.hasNext()) {
                    String key = it.next();
                    System.out.println(String.format("%s %s %s", c.key, key,  mappings.get(key).type()));
                }
            }
        }
    }

    protected void dumpDocument(String index, String type, String id) throws Exception {
        ThreadContext threadContext = client().threadPool().getThreadContext();
        try (StoredContext cxt = threadContext.stashContext()) {
            threadContext.putHeader(ConfigConstants.SG_CONF_REQUEST_HEADER, "true");
            GetResponse response = client().prepareGet(index, type, id).get();
            System.out.println(response.getSourceAsString());
        }
    }
    
    protected XContentBuilder createSimpleDocument(String value) throws IOException {
        return XContentFactory.jsonBuilder()
            .startObject()
                .field("@timestamp", "1970.01.01")
                .field("msg", value)
            .endObject();
    }

    protected void givenUserIsClusterAdmin(String user) {
        expSubjectAccessReviewToBe(Boolean.TRUE, user);
    }

    protected void givenUserIsNotClusterAdmin(String user) {
        expSubjectAccessReviewToBe(Boolean.FALSE, user);
    }

    private void expSubjectAccessReviewToBe(boolean value, String user) {
        testContext.put(USERNAME, user);
        SubjectAccessReviewResponse response = new SubjectAccessReviewResponse(value, "v1", null, null, null,
                "");
        apiServer.expect()
            .post()
            .withPath("/apis/authorization.openshift.io/v1/subjectaccessreviews")
            .andReturn(201, response)
            .withHeader("Authorization", "Bearer " + user + "-token")
            .always();
        apiServer.expect()
            .get()
            .withPath("/apis/user.openshift.io/v1/users/~")
            .andReturn(200, "{ \"metadata\": { \"name\": \"" + user.replace("\\", "\\\\") + "\" } }")
            .withHeader("Authorization", "Bearer " + user + "-token")
            .always();
    }

    protected void givenUserHasBadToken() {
        givenUserHasBadToken("");
    }
    
    protected void givenUserHasBadToken(String token) {
        apiServer.expect()
            .get()
            .withPath("/apis/user.openshift.io/v1/users/~")
            .andReturn(401, "")
            .withHeader("Authorization", "Bearer bogusToken")
            .always();
    }
    
    protected String formatProjectIndexPattern(String project, String uid) {
        return String.format("project.%s.%s.*", project, uid);
    }

    protected void whenContextIsForUser(String user) {
        testContext.put(USERNAME, user);
    }

    protected void givenUserIsAdminForProjects(String... projects) throws Exception {
        String user = (String) testContext.get(USERNAME);
        ProjectListBuilder builder = new ProjectListBuilder(false);
        for (String project : projects) {
            builder.addToItems(new ProjectBuilder(false).withNewMetadata().withUid("uuid").withName(project).endMetadata().build());
        }
        apiServer.expect()
            .withPath("/apis/project.openshift.io/v1/projects")
            .andReturn(200, builder.build())
            .withHeader("Authorization", "Bearer " + user + "-token")
            .always();
    }

    protected void givenUserIsAdminForProject(String name, String uuid) throws Exception {
        String user = (String) testContext.get(USERNAME);
        ProjectListBuilder builder = new ProjectListBuilder(false);
        builder.addToItems(new ProjectBuilder(false).withNewMetadata().withUid(uuid).withName(name).endMetadata().build());
        apiServer.expect()
            .withPath("/apis/project.openshift.io/v1/projects")
            .andReturn(200, builder.build())
            .withHeader("Authorization", "Bearer " + user + "-token")
            .always();
    }

    protected void whenSearchingProjects(String... projects) throws Exception {
        testContext.put(URI, "_msearch");
        String indicies = Arrays.asList(projects).stream()
                .map(p -> formatProjectIndexPattern(p,p))
                .collect(Collectors.joining("\",\""));
        String body = new StringBuilder()
            .append(String.format("{\"index\": [\"%s\"]}\r\n", indicies))
            .append("{\"size\":0,\"query\":{\"match_all\":{}}}\r\n")
            .toString();
        RequestRunner runner = new RequestRunner.Builder()
            .server(getHttpServerUri())
            .keyStore(keyStore)
            .keyStorePswd(password)
            .username((String) testContext.get(USERNAME))
            .method("POST")
            .header("Content-Type", "application/x-ndjson")
            .body(body)
            .build();
        Response response = runner.run("_msearch");
        testContext.put(RESPONSE, response);
    }

    protected void whenGettingDocument(String uri) throws Exception {
        whenGettingDocument(uri, null);
    }
    
    protected void whenGettingDocument(String uri, Headers headers) throws Exception {
        testContext.put(URI, uri);
        RequestRunner runner = new RequestRunner.Builder()
                .server(getHttpServerUri())
                .keyStore(keyStore)
                .keyStorePswd(password)
                .username((String) testContext.get(USERNAME))
                .build();
        testContext.put(RESPONSE, headers == null ? runner.run(uri) : runner.run(uri, headers));
    }

    protected void whenCheckingIndexExists(String uri) throws Exception {
        testContext.put(URI, uri);
        RequestRunner runner = new RequestRunner.Builder()
                .server(getHttpServerUri())
                .keyStore(keyStore)
                .keyStorePswd(password)
                .username((String) testContext.get(USERNAME)).method("head")
                .build();
        testContext.put(RESPONSE, runner.run(uri));
    }

    protected void assertThatResponseIsSuccessful() {
        String username = (String) testContext.get(USERNAME);
        Response response = (Response) testContext.get(RESPONSE);
        String uri = (String) testContext.get(URI);
        assertEquals(String.format("Exp. %s request to succeed for %s", username, uri), 200, response.code());
    }

    protected void assertThatResponseIsForbidden() {
        String username = (String) testContext.get(USERNAME);
        Response response = (Response) testContext.get(RESPONSE);
        String uri = (String) testContext.get(URI);
        assertEquals(String.format("Exp. %s to be forbidden for %s", username, uri), 403, response.code());
    }

    protected void assertThatResponseIsUnauthorized() {
        String username = (String) testContext.get(USERNAME);
        Response response = (Response) testContext.get(RESPONSE);
        String uri = (String) testContext.get(URI);
        assertEquals(String.format("Exp. %s to be unauthorized for %s", username, uri), 401, response.code());
    }
    
    protected Client client() {
        return esNode1.client();
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

            final List<NodeInfo> nodes = res.getNodes();

            for (NodeInfo nodeInfo : nodes) {
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

    protected String executeSimpleRequest(final String user, final String uri) throws Exception {

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder();
        builder.url(getHttpServerUri() + "/" + uri)
            .addHeader("X-Proxy-Remote-User", user)
            .addHeader("X-Forwarded-For", "127.0.0.1")
            .addHeader("Authorization", "Bearer testtoken");

        return executeSimpleRequest(builder);
    }

    private String executeSimpleRequest(final okhttp3.Request.Builder builder) throws Exception{

        OkHttpClient client = getHttpClient();
        Response response = client.newCall(builder.build()).execute();
        if (response.code() >= 300) {
            throw new RuntimeException("Statuscode " + response.code());
        }
        return response.body().string();
    }

    protected Response executeGetRequest(final String uri, Headers headers) throws Exception {
        return executeRequest(uri, headers, "GET", null);
    }

    protected Response executeHeadRequest(final String uri, Headers headers) throws Exception {
        return executeRequest(uri, headers, "HEAD", null);
    }

    protected Response executePutRequest(final String uri, String body, Headers headers) throws Exception {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
        return executeRequest(uri, headers, "PUT", requestBody);
    }

    protected Response executePostRequest(final String uri, String body, Headers headers) throws Exception {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
        return executeRequest(uri, headers, "POST", requestBody);
    }

    protected Response executeDeleteRequest(final String uri, Headers headers) throws Exception {
        return executeRequest(uri, headers, "DELETE", null);
    }

    protected Response executeRequest(final String uri, Headers headers, String method, RequestBody body) throws Exception {
        OkHttpClient client = getHttpClient();
        Request.Builder builder = new Request.Builder();
        okhttp3.Request request = builder.headers(headers)
            .url(getHttpServerUri() + "/" + uri)
            .method(method, body)
            .build();
        Response response = client.newCall(request).execute();
        if(log.isTraceEnabled()) {
            log.trace(response.body().string());
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

    protected final String loadFile(final String file) throws IOException {
        final StringWriter sw = new StringWriter();
        IOUtils.copy(this.getClass().getResourceAsStream("/" + file), sw, StandardCharsets.UTF_8.toString());
        return sw.toString();
    }

    protected BytesReference readYmlContent(final String file) {
        try {
            return readXContent(new StringReader(loadFile(file)), XContentType.YAML);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static BytesReference readXContent(final Reader reader, final XContentType contentType) throws IOException {
        XContentParser parser = null;
        try {
            parser = XContentFactory.xContent(contentType).createParser(NamedXContentRegistry.EMPTY, reader);
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

    protected String formatUserName(String username) {
        if (username.contains("\\")) {
            return username.replace("\\", "/");
        }
        return username;
    }
}
