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
package io.fabric8.elasticsearch.plugin.acl;

import static io.fabric8.elasticsearch.plugin.kibana.KibanaSeed.setDashboards;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.NoShardAvailableActionException;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.engine.DocumentMissingException;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestFilter;
import org.elasticsearch.rest.RestFilterChain;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateAction;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateRequest;
import com.floragunn.searchguard.action.configupdate.ConfigUpdateResponse;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.floragunn.searchguard.ssl.util.SSLConfigConstants;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.ClusterRoleBinding;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * REST filter to update the ACL when a user
 * first makes a request
 * @author jeff.cantrill
 *
 */
public class DynamicACLFilter
	extends RestFilter
	implements ConfigurationSettings {

	private static final String AUTHORIZATION_HEADER = "Authorization";

	private final ESLogger logger;
	private final UserProjectCache cache;
	private final String proxyUserHeader;
	private final Client esClient;
	private final String searchGuardIndex;
	private final String kibanaIndex;
	private final String kibanaVersion;
	private final String userProfilePrefix;
	private final Settings settings;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition syncing = lock.newCondition();

	private final String kbnVersionHeader;
	private final String[] operationsProjects;

	private Boolean enabled;
	private Boolean seeded;

	private final boolean use_cdm;
	private final String cdm_project_prefix;

	@Inject
	public DynamicACLFilter(final UserProjectCache cache, final Settings settings, final Client client){
		this.cache = cache;
		this.logger = Loggers.getLogger(getClass(), settings);
		this.proxyUserHeader = settings.get(SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, DEFAULT_AUTH_PROXY_HEADER);
		this.searchGuardIndex = settings.get(SEARCHGUARD_CONFIG_INDEX_NAME, DEFAULT_SECURITY_CONFIG_INDEX);
		this.userProfilePrefix = settings.get(OPENSHIFT_ES_USER_PROFILE_PREFIX, DEFAULT_USER_PROFILE_PREFIX);
		this.kibanaIndex = settings.get(KIBANA_CONFIG_INDEX_NAME, DEFAULT_USER_PROFILE_PREFIX);
		this.kibanaVersion = settings.get(KIBANA_CONFIG_VERSION, DEFAULT_KIBANA_VERSION);
		this.kbnVersionHeader = settings.get(KIBANA_VERSION_HEADER, DEFAULT_KIBANA_VERSION_HEADER);

		this.operationsProjects = settings.getAsArray(OPENSHIFT_CONFIG_OPS_PROJECTS, DEFAULT_OPENSHIFT_OPS_PROJECTS);
		this.use_cdm = settings.getAsBoolean(OPENSHIFT_CONFIG_USE_COMMON_DATA_MODEL, OPENSHIFT_DEFAULT_USE_COMMON_DATA_MODEL);
		this.cdm_project_prefix = settings.get(OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX, OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX);

		logger.debug("searchGuardIndex: {}", this.searchGuardIndex);

		this.settings = settings;

		this.seeded = false;
		this.enabled = settings.getAsBoolean(OPENSHIFT_DYNAMIC_ENABLED_FLAG, OPENSHIFT_DYNAMIC_ENABLED_DEFAULT);
		// This is to not have SG print an error with us loading the SG_SSL plugin for our transport client
		System.setProperty("sg.nowarn.client", "true");

		/** Build the esClient as a transport client **/
		String clusterName = settings.get("cluster.name");
		String keystore = settings.get(SG_CLIENT_KS_PATH, DEFAULT_SG_CLIENT_KS_PATH);
		String truststore = settings.get(SG_CLIENT_TS_PATH, DEFAULT_SG_CLIENT_TS_PATH);
		String kspass = settings.get(SG_CLIENT_KS_PASS, DEFAULT_SG_CLIENT_KS_PASS);
		String tspass = settings.get(SG_CLIENT_TS_PASS, DEFAULT_SG_CLIENT_TS_PASS);
		String kstype = settings.get(SG_CLIENT_KS_TYPE, DEFAULT_SG_CLIENT_KS_TYPE);
		String tstype = settings.get(SG_CLIENT_TS_TYPE, DEFAULT_SG_CLIENT_TS_TYPE);

		Settings.Builder settingsBuilder = Settings
                .builder()
                .put("path.home", ".")
                .put("path.conf", ".")
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_FILEPATH, keystore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_FILEPATH, truststore)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_PASSWORD, kspass)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_PASSWORD, tspass)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENFORCE_HOSTNAME_VERIFICATION_RESOLVE_HOST_NAME, false)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, true)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_KEYSTORE_TYPE, kstype)
                .put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_TRUSTSTORE_TYPE, tstype)

                .put("cluster.name", clusterName)
                .put("client.transport.ignore_cluster_name", false)
                .put("client.transport.sniff", false);

        Settings clientSettings = settingsBuilder.build();

		this.esClient = TransportClient.builder().settings(clientSettings).addPlugin(SearchGuardSSLPlugin.class)
                .addPlugin(SearchGuardPlugin.class) //needed for config update action only
                .build()
                .addTransportAddress(new InetSocketTransportAddress(new InetSocketAddress("localhost", 9300)));
	}

	@Override
	public void process(RestRequest request, RestChannel channel, RestFilterChain chain) throws Exception {
		boolean continue_processing = true;

		try {
			if (!seeded) {
				try {
					lock.lock();
					seedInitialACL(esClient);
					syncing.signalAll();
				}
				catch (Exception e) {
					logger.error("Exception encountered when seeding initial ACL", e);
				}
				finally{
					lock.unlock();
				}
			}

			if ( enabled ) {
				// grab the kibana version here out of "kbn-version" if we can
				// -- otherwise use the config one
				String kbnVersion = getKibanaVersion(request);
				if ( StringUtils.isEmpty(kbnVersion) )
					kbnVersion = kibanaVersion;

				final String user = getUser(request);
				final String token = getBearerToken(request);
				if(logger.isDebugEnabled()){
					logger.debug("Handling Request...");
					logger.debug("Evaluating request for user '{}' with a {} token", user,
							(StringUtils.isNotEmpty(token) ? "non-empty" : "empty"));
					logger.debug("Cache has user: {}", cache.hasUser(user, token));
				}
				if (StringUtils.isNotEmpty(token) && StringUtils.isNotEmpty(user) && !cache.hasUser(user, token)) {
					final boolean isOperationsUser = isOperationsUser(user);
					if(isOperationsUser){
						request.putInContext(OPENSHIFT_ROLES, "cluster-admin");
					}
					if(updateCache(user, token, isOperationsUser, kbnVersion)){
						syncAcl();
					}

				}
			}
		} catch (ElasticsearchSecurityException ese) {
			logger.info("Could not authenticate user");
			channel.sendResponse(new BytesRestResponse(RestStatus.UNAUTHORIZED));
			continue_processing = false;
		} catch (Exception e) {
			logger.error("Error handling request in {}", e, this.getClass().getSimpleName());
		} finally {
			if (continue_processing) {
				chain.continueProcessing(request, channel);
			}
		}
	}

	private String getUser(RestRequest request) {
		return (String) ObjectUtils.defaultIfNull(request.header(proxyUserHeader), "");
	}

	private String getBearerToken(RestRequest request) {
		final String[] auth = ((String) ObjectUtils.defaultIfNull(request.header(AUTHORIZATION_HEADER), "")).split(" ");
		if (auth.length >= 2 && "Bearer".equals(auth[0])) {
			return auth[1];
		}
		return "";
	}

	private String getKibanaVersion(RestRequest request) {
		return (String) ObjectUtils.defaultIfNull(request.header(kbnVersionHeader), "");
	}

	private boolean updateCache(final String user, final String token, final boolean isOperationsUser, final String kbnVersion) {
		logger.debug("Updating the cache for user '{}'", user);
		try{
			// This is the key to authentication.  Before listProjectsFor, we haven't actually authenticated
			// anything in this plugin, using the given token.  If the token is valid, we will get back a
			// list of projects for the token.  If not, listProjectsFor will throw an exception and we will
			// not update the cache with the user and token.  In this way we will keep bogus entries out of
			// the cache.
			Set<String> projects = listProjectsFor(token);
			cache.update(user, token, projects, isOperationsUser);

			Set<String> roles = new HashSet<String>();
			if (isOperationsUser)
				roles.add("operations-user");

			setDashboards(user, projects, roles, esClient, kibanaIndex, kbnVersion, use_cdm, cdm_project_prefix, settings);
		} catch (KubernetesClientException e) {
			logger.error("Error retrieving project list for '{}'",e, user);
			throw new ElasticsearchSecurityException(e.getMessage());
		} catch (Exception e) {
			logger.error("Error retrieving project list for '{}'",e, user);
			return false;
		}
		return true;
	}

	// WARNING: This function must perform authentication with the given token.  This
	// is the only authentication performed in this plugin.  This function must throw
	// an exception if the token is invalid.
	private Set<String> listProjectsFor(final String token) throws Exception{
		ConfigBuilder builder = new ConfigBuilder()
				.withOauthToken(token);
		Set<String> names = new HashSet<>();
		try(OpenShiftClient client = new DefaultOpenShiftClient(builder.build())){
			List<Project> projects = client.projects().list().getItems();
			for (Project project : projects) {
				if ( ! isBlacklistProject(project.getMetadata().getName()) )
					names.add(project.getMetadata().getName() + "." + project.getMetadata().getUid());
			}
		}
		return names;
	}

	private boolean isBlacklistProject(String project) {
		return ArrayUtils.contains(operationsProjects, project.toLowerCase());
	}

	private boolean isOperationsUser(final String username){
		ConfigBuilder builder = new ConfigBuilder();
		
		boolean clusterReaderAllowed = settings.getAsBoolean(OPENSHIFT_ALLOW_CLUSTER_READER, DEFAULT_OPENSHIFT_ALLOW_CLUSTER_READER);

		try (OpenShiftClient osClient = new DefaultOpenShiftClient(builder.build())) {
			ClusterRoleBinding bResponse = osClient.clusterRoleBindings().inNamespace("").withName("cluster-admins").get();
			
			logger.debug("Does '{}' exist in cluster-admins? '{}'", username, bResponse.getUserNames().contains(username));

			if ( clusterReaderAllowed ) {
				ClusterRoleBinding readerResponse = osClient.clusterRoleBindings().inNamespace("").withName("cluster-readers").get();

				logger.debug("Does '{}' exist in cluster-readers? '{}'", username, readerResponse.getUserNames().contains(username));
				
				if ( readerResponse.getUserNames().contains(username) )
					return true;
			}
			
			return bResponse.getUserNames().contains(username);
		}
		catch(Exception e){
			logger.error("Exception determining user's role.", e);
		}
		
		return false;
	}

	private synchronized void syncAcl() {
		logger.debug("Syncing the ACL to ElasticSearch");
		try {
			logger.debug("Loading SearchGuard ACL...");

			SearchGuardRoles roles = readRolesACL(esClient);
			SearchGuardRolesMapping rolesMapping = readRolesMappingACL(esClient);

			logger.debug("Syncing from cache to ACL...");
			roles.syncFrom(cache, userProfilePrefix, cdm_project_prefix);
			rolesMapping.syncFrom(cache, userProfilePrefix);

			writeACL(esClient, roles, rolesMapping);
		} catch (Exception e) {
			logger.error("Exception while syncing ACL with cache", e);
		}
	}

	private SearchGuardRoles readRolesACL(Client esClient) throws IOException {
		GetRequest getRequest = esClient.prepareGet(searchGuardIndex, SEARCHGUARD_ROLE_TYPE, SEARCHGUARD_CONFIG_ID)
				.setRefresh(true).request();
		GetResponse response = esClient.get(getRequest).actionGet(); // need to worry about timeout?

		SearchGuardRoles roles = new SearchGuardRoles().load(response.getSource());
		logger.debug("Read in roles '{}'", roles);
		return roles;
	}

	private SearchGuardRolesMapping readRolesMappingACL(Client esClient) throws IOException {
		GetRequest getRequest = esClient.prepareGet(searchGuardIndex, SEARCHGUARD_MAPPING_TYPE, SEARCHGUARD_CONFIG_ID)
				.setRefresh(true).request();
		GetResponse response = esClient.get(getRequest).actionGet(); // need to worry about timeout?

		SearchGuardRolesMapping rolesMapping = new SearchGuardRolesMapping().load(response.getSource());
		logger.debug("Read in rolesMapping '{}'", rolesMapping);
		return rolesMapping;
	}

	private void writeACL(Client esClient, SearchGuardRoles roles, SearchGuardRolesMapping rolesMapping) throws IOException {
		IndexRequest rolesIR = new IndexRequest(searchGuardIndex).type(SEARCHGUARD_ROLE_TYPE).id(SEARCHGUARD_CONFIG_ID).refresh(true)
        .consistencyLevel(WriteConsistencyLevel.DEFAULT)
        .source(roles.toMap());

		String rID = esClient.index(rolesIR).actionGet().getId();
		logger.debug("Built roles request: '{}'", rolesIR);
		logger.debug("Roles ID: '{}'", rID);

		IndexRequest mappingIR = new IndexRequest(searchGuardIndex).type(SEARCHGUARD_MAPPING_TYPE).id(SEARCHGUARD_CONFIG_ID).refresh(true)
                .consistencyLevel(WriteConsistencyLevel.DEFAULT)
                .source(rolesMapping.toMap());

		String rmID = esClient.index(mappingIR).actionGet().getId();
		logger.debug("Built rolesMapping request: '{}'", mappingIR);
		logger.debug("rolesMapping ID: '{}'", rmID);

		// force a config reload
		String[] updateString = {SEARCHGUARD_ROLE_TYPE, SEARCHGUARD_MAPPING_TYPE};
		ConfigUpdateResponse cur = esClient.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(updateString)).actionGet();

		if ( cur.getNodes().length > 0 )
			logger.debug("Successfully reloaded config with '{}' nodes", cur.getNodes().length);
		else
			logger.warn("Failed to reloaded configs", cur.getNodes().length);
	}

	private void createConfig(Client esClient) throws IOException {

		final ClusterHealthResponse chr = esClient.admin().cluster().health(new ClusterHealthRequest()).actionGet();

        if ( ! chr.isTimedOut() ) {
            
            if(!doesSearchGuardIndexExist()) {
                esClient.admin().indices().create( new CreateIndexRequest().index(searchGuardIndex)
                        .settings("index.number_of_shards", 1, "index.number_of_replicas", chr.getNumberOfDataNodes()-1)
                        ).actionGet().isAcknowledged();
            }

			// Wait for health status of YELLOW
			ClusterHealthRequest healthRequest = new ClusterHealthRequest()
					.indices(new String[]{searchGuardIndex}).waitForYellowStatus();

			esClient.admin().cluster().health(healthRequest).actionGet().getStatus();

			String cd = settings.get(SG_CONFIG_SETTING_PATH, DEFAULT_SG_CONFIG_SETTING_PATH);
			// create initial sg config
			for ( String type : SEARCHGUARD_INITIAL_CONFIGS ) {
				String file = String.format("%ssg_%s.yml", cd, type);
				logger.debug("Using '{}' file to populate '{}' type", file, type);
				if ( ! uploadFile(esClient, file, type) ) {
					String failedSeed = String.format("'%s/%s/%s' with '%s'", searchGuardIndex, type, SEARCHGUARD_CONFIG_ID, file);
					logger.error("Was not able to seed {}", failedSeed);
				}
			}

            ConfigUpdateResponse cur = esClient.execute(ConfigUpdateAction.INSTANCE, new ConfigUpdateRequest(SEARCHGUARD_INITIAL_CONFIGS)).actionGet();
            logger.debug("Succeed seeding SG, all node's config updated? '{}'", cur.getNodes().length == chr.getNumberOfNodes());
        }
	}

	private void seedInitialACL(Client esClient) throws Exception {

		SearchGuardRoles roles = new SearchGuardRoles();
		SearchGuardRolesMapping rolesMapping = new SearchGuardRolesMapping();
		try {
			if ( doesSearchGuardIndexExist()) {

				//This should return nothing initially - if it does, we're done
				roles = readRolesACL(esClient);
				rolesMapping = readRolesMappingACL(esClient);

				if ( roles.iterator().hasNext() || rolesMapping.iterator().hasNext() ) {
					if ( logger.isDebugEnabled() )
						logger.debug("Have already seeded");
					seeded = true;
					return;
				}
			}
		}
		catch (NoNodeAvailableException | NoShardAvailableActionException e) {
			logger.warn("Trying to seed ACL when ES has not not yet started: '{}'", e.getMessage());
			return;
		}
		catch (IndexNotFoundException | DocumentMissingException | NullPointerException e) {
			logger.debug("Caught Exception, ACL has not been seeded yet", e);
		}
		catch (Exception e) {
			logger.error("Error checking ACL when seeding", e);
			throw e;
		}

		try {
			// seed here from the files
			createConfig(esClient);
		}
		catch (Exception e) {
			logger.error("Error seeding initial ACL", e);
			throw e;
		}

		seeded = true;
	}
	
	private boolean doesSearchGuardIndexExist() {
	    return esClient.admin().indices().exists(new IndicesExistsRequest().indices(new String[]{searchGuardIndex})).actionGet().isExists();
	}

	@Override
	public int order() {
		// need to run before search guard
		return Integer.MIN_VALUE;
	}

	private boolean uploadFile(Client tc, String filepath, String type) {
        try (Reader reader = new FileReader(filepath)) {

            final String id = tc
                    .index(new IndexRequest(searchGuardIndex).type(type).id(SEARCHGUARD_CONFIG_ID).refresh(true)
                            .consistencyLevel(WriteConsistencyLevel.DEFAULT).source(readXContent(reader, XContentType.YAML)))
                            .actionGet().getId();

            // Verify that the ID we tried to update is the update that was updated
            if (SEARCHGUARD_CONFIG_ID.equals(id)) {
                return true;
            } else {
                logger.warn("Configuration for '{}' failed for unknown reasons", type);
            }
        } catch (IOException e) {
            logger.error("Configuration for '{}' failed due to '{}'", type, e);
        }

        return false;
    }

    private BytesReference readXContent(final Reader reader, final XContentType xContentType) throws IOException {
        XContentParser parser = null;
        try {
            parser = XContentFactory.xContent(xContentType).createParser(reader);
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
}
