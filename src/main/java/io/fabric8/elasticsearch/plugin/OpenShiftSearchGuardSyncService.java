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

import static org.elasticsearch.node.NodeBuilder.*;

import io.fabric8.elasticsearch.plugin.acl.SearchGuardACL;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.internal.Utils;
import io.fabric8.openshift.api.model.NamedRoleBinding;
import io.fabric8.openshift.api.model.PolicyBinding;
import io.fabric8.openshift.api.model.PolicyBindingList;
import io.fabric8.openshift.client.DefaultOpenshiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenshiftConfig;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.PrimaryMissingActionException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.LocalNodeMasterListener;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.threadpool.ThreadPool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class OpenShiftSearchGuardSyncService 
		extends AbstractLifecycleComponent<OpenShiftSearchGuardSyncService>
		implements LocalNodeMasterListener, Watcher<PolicyBinding> {
	
	private static final String SETTINGS_PREFIX = "os.sg.sync.";
	private static final String SEARCHGUARD_TYPE = "ac";
	private static final String SEARCHGUARD_ID = "ac";
	private static final List<String> loggingRoles = Arrays.asList("admins");

	private final ClusterService clusterService;
	private String searchGuardIndex;

	private ObjectMapper mapper = new ObjectMapper();
	private OpenShiftClient osClient;
	private Watch watch;
	private Node esNode;
	private Client esClient;
	private OpenShiftPolicyCache cache;

	@Inject
	protected OpenShiftSearchGuardSyncService(Settings settings, ClusterService clusterService) {
		super(settings);

		this.osClient = new DefaultOpenshiftClient(initConfig());
		this.clusterService = clusterService;
		searchGuardIndex = Utils.getSystemPropertyOrEnvVar("AOS_SG_INDEX","searchguard");
		
		logger.info("Initialized {}", getClass().getSimpleName());
	}

	private Config initConfig() {
		ConfigBuilder builder = new ConfigBuilder();
		final String caPath = Utils.getSystemPropertyOrEnvVar("AOS_SG_SYNC_CA_PATH",
				OpenshiftConfig.KUBERNETES_SERVICE_ACCOUNT_CA_CRT_PATH);
		final String tokenPath = Utils.getSystemPropertyOrEnvVar("AOS_SG_SYNC_SA_PATH",
				OpenshiftConfig.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH);
		final String masterApiUrl = Utils.getSystemPropertyOrEnvVar("K8S_HOST_URL");

		// validate the required values

		builder.withMasterUrl(masterApiUrl).withCaCertFile(caPath);
		try {
			if (Files.isRegularFile(Paths.get(tokenPath))) {
				String serviceToken = new String(Files.readAllBytes(Paths.get(tokenPath)));
				if (serviceToken != null) {
					builder.withOauthToken(serviceToken);
				}
			}
		} catch (IOException e) {
			logger.error("Exception while configuring OpenShift SearchGuard Sync Service: {}", e);
		}
		return builder.build();
	}

	@Override
	protected void doStart() throws ElasticsearchException {
		logger.debug("Starting OpenShift Policy Sync Plugin");
		clusterService.add(this);
	}

	@Override
	protected void doStop() throws ElasticsearchException {
		logger.debug("Stopping OpenShift Policy Sync Plugin");
		if (watch != null && watch.isOpen()) {
			watch.close();
		}
		logger.debug("Stopped watching OpenShift policy bindings");
		esNode.close();
		clusterService.remove(this);
	}

	@Override
	protected void doClose() throws ElasticsearchException {
		logger.debug("Stopping watching OpenShift policy bindings");
		if (watch != null && watch.isOpen()) {
			watch.close();
		}
		logger.debug("Stopped watching OpenShift policy bindings");
	}

	@Override
	public void onMaster() {
		logger.debug("Elected as Master. Starting watch of policy bindings");
		PolicyBindingList policyBinding = osClient.policyBindings().list();
		final String resourceVersion = policyBinding.getMetadata().getResourceVersion();

		esNode = nodeBuilder().node();
		esClient = esNode.client();

		initBindingCache();
		seedAcls();
		
		logger.debug("Starting watch of OpenShift policy bindings");
		watch = osClient.policyBindings().watch(resourceVersion, this);
		logger.debug("Started watching OpenShift policy bindings"); 
	}
	
	@Override
	public synchronized void eventReceived(Action action, PolicyBinding policyBinding) {
		logger.debug("Received {}: {}", action, policyBinding);
		boolean updateAcl = false;
		// retrieve allowable roles from config
		final SearchGuardACL acl = retrieveAcl();
		for (NamedRoleBinding binding : policyBinding.getRoleBindings()) {
			if(loggingRoles.contains(binding.getName())){
				updateAcl = true;
				logger.debug("Trying to update cache for binding {}", binding.getName());
				cache.update(binding.getRoleBinding());
			}
		}
		if(updateAcl){
			acl.syncFrom(cache);
			update(acl);
		}
	}

	private void seedAcls() {
		SearchGuardACL acls = retrieveAcl();
		acls.syncFrom(cache);
		update(acls);
	}

	private void initBindingCache() {
		logger.debug("Initializing project->user cache");
		cache = new ProjectUserCache(logger);
		
		List<Namespace> projects = osClient.namespaces().list().getItems();
		for (Namespace project : projects) {
			String projectName = project.getMetadata().getName();
			logger.debug("evaluating project {}", projectName);
			List<PolicyBinding> projectBindings = osClient.policyBindings().inNamespace(projectName).list().getItems();
			for (PolicyBinding projectBinding : projectBindings) {
				logger.debug("-> evaluating policy binding {}", projectBinding.getMetadata().getName());
				for (NamedRoleBinding role : projectBinding.getRoleBindings()) {
					logger.debug("--> evaluating role binding {}", role.getName());
					if(loggingRoles.contains(role.getName())){
						cache.add(projectName, role);
					}
				}
			}
		}

	}

	private void update(SearchGuardACL acl) {
		try {
			if(logger.isDebugEnabled()){
				logger.debug("Writing ACLs {}",
						mapper.writer(new DefaultPrettyPrinter()).writeValueAsString(acl));
			}
			esClient.prepareUpdate(searchGuardIndex, SEARCHGUARD_TYPE, SEARCHGUARD_ID)
					.setDoc(mapper.writeValueAsBytes(acl)).execute();
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Unable to update the SearchGuard ACL", e);
		}
	}

	private SearchGuardACL retrieveAcl() {
		try {
			GetResponse response = esClient.prepareGet(searchGuardIndex, SEARCHGUARD_TYPE, SEARCHGUARD_ID).execute()
					.actionGet(); // need to worry about timeout?
			return mapper.readValue(response.getSourceAsBytes(), SearchGuardACL.class);
		} catch (IOException e) {
			throw new RuntimeException("Unable to rerieve the SearchGuard ACL", e);
		}

	}

	@Override
	public void offMaster() {
		logger.debug("Stopping watching OpenShift policy bindings");
		if (watch != null && watch.isOpen()) {
			watch.close();
		}
		logger.debug("Stopped watching OpenShift policy bindings");
	}

	@Override
	public String executorName() {
		return ThreadPool.Names.GENERIC;
	}

	@Override
	public void onClose(KubernetesClientException e) {
		logger.error("PolicyBinding Watch exception", e);
	}
}
