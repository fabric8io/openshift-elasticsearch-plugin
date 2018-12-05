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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.http.HttpServerTransport;
import org.elasticsearch.http.HttpServerTransport.Dispatcher;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.indices.breaker.CircuitBreakerService;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.NetworkPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.Transport;
import org.elasticsearch.transport.TransportInterceptor;
import org.elasticsearch.watcher.ResourceWatcherService;

import com.floragunn.searchguard.SearchGuardPlugin;

import io.fabric8.elasticsearch.plugin.acl.ACLDocumentManager;
import io.fabric8.elasticsearch.plugin.acl.DynamicACLFilter;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardSyncStrategyFactory;
import io.fabric8.elasticsearch.plugin.filter.FieldStatsResponseFilter;
import io.fabric8.elasticsearch.plugin.kibana.IndexMappingLoader;
import io.fabric8.elasticsearch.plugin.kibana.KibanaSeed;
import io.fabric8.elasticsearch.plugin.kibana.KibanaUtils;
import io.fabric8.elasticsearch.util.RequestUtils;

public class OpenShiftElasticSearchPlugin extends Plugin implements ConfigurationSettings, ActionPlugin, NetworkPlugin {

    private final Settings settings;
    private DynamicACLFilter aclFilter;
    private SearchGuardPlugin sgPlugin;

    public OpenShiftElasticSearchPlugin(final Settings settings) {
        this.settings = settings;
        this.sgPlugin = new SearchGuardPlugin(settings);
    }

    public Collection<Object> createComponents(Client client, ClusterService clusterService, ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService, ScriptService scriptService,
            NamedXContentRegistry namedXContentRegistry) {

        final PluginSettings pluginSettings = new PluginSettings(settings);
        final IndexMappingLoader indexMappingLoader = new IndexMappingLoader(settings);
        final PluginClient pluginClient = new PluginClient(client, threadPool.getThreadContext());
        final OpenshiftAPIService apiService = new OpenshiftAPIService();
        final RequestUtils requestUtils = new RequestUtils(pluginSettings, apiService);
        final OpenshiftRequestContextFactory contextFactory = new OpenshiftRequestContextFactory(settings, requestUtils,
                apiService);
        final SearchGuardSyncStrategyFactory documentFactory = new SearchGuardSyncStrategyFactory(pluginSettings);
        final KibanaUtils kUtils = new KibanaUtils(pluginSettings, pluginClient);
        final KibanaSeed seed = new KibanaSeed(pluginSettings, indexMappingLoader, pluginClient, kUtils);
        final ACLDocumentManager aclDocumentManager = new ACLDocumentManager(pluginClient, pluginSettings, documentFactory, threadPool);
        this.aclFilter = new DynamicACLFilter(pluginSettings, seed, client, threadPool, requestUtils, aclDocumentManager);
        OpenShiftElasticSearchService osElasticService = new OpenShiftElasticSearchService(aclDocumentManager, pluginSettings);
        clusterService.addLocalNodeMasterListener(osElasticService);
        
        PluginServiceFactory.setApiService(apiService);
        PluginServiceFactory.setContextFactory(contextFactory);
        PluginServiceFactory.setThreadContext(threadPool.getThreadContext());
        PluginServiceFactory.markReady();

        List<Object> list = new ArrayList<>();
        list.add(aclDocumentManager);
        list.add(pluginSettings);
        list.add(indexMappingLoader);
        list.add(pluginClient);
        list.add(requestUtils);
        list.add(apiService);
        list.add(contextFactory);
        list.add(documentFactory);
        list.add(kUtils);
        list.add(seed);
        list.add(aclFilter);
        list.add(osElasticService);
        list.add(new FieldStatsResponseFilter(pluginClient));
        list.addAll(sgPlugin.createComponents(client, clusterService, threadPool, resourceWatcherService, scriptService,
                namedXContentRegistry));
        return list;
    }

    @Override
    public UnaryOperator<RestHandler> getRestHandlerWrapper(final ThreadContext threadContext) {
        return (rh) -> aclFilter.wrap(rh, sgPlugin.getRestHandlerWrapper(threadContext));
    }
    
    @Override
    public List<RestHandler> getRestHandlers(Settings settings, RestController restController,
            ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings, SettingsFilter settingsFilter,
            IndexNameExpressionResolver indexNameExpressionResolver, Supplier<DiscoveryNodes> nodesInCluster) {
        List<RestHandler> list = new ArrayList<>();
        list.addAll(sgPlugin.getRestHandlers(settings, restController, clusterSettings, indexScopedSettings,
                settingsFilter, indexNameExpressionResolver, nodesInCluster));
        return list;
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> list = new ArrayList<>();
        list.addAll(sgPlugin.getActions());
        return list;
    }

    @Override
    public void onIndexModule(IndexModule indexModule) {
        sgPlugin.onIndexModule(indexModule);
    }

    @Override
    public List<Class<? extends ActionFilter>> getActionFilters() {
        List<Class<? extends ActionFilter>> list = new ArrayList<>();
        list.add(FieldStatsResponseFilter.class);
        list.addAll(sgPlugin.getActionFilters());
        return list;
    }

    @Override
    public List<TransportInterceptor> getTransportInterceptors(ThreadContext threadContext) {
        List<TransportInterceptor> list = new ArrayList<>();
        list.addAll(sgPlugin.getTransportInterceptors(threadContext));
        return list;
    }

    @Override
    public Map<String, Supplier<Transport>> getTransports(Settings settings, ThreadPool threadPool, BigArrays bigArrays,
            CircuitBreakerService circuitBreakerService, NamedWriteableRegistry namedWriteableRegistry,
            NetworkService networkService) {
        Map<String, Supplier<Transport>> transports = sgPlugin.getTransports(settings, threadPool, bigArrays, circuitBreakerService, namedWriteableRegistry,
                networkService);
        return transports;
    }

    @Override
    public Map<String, Supplier<HttpServerTransport>> getHttpTransports(Settings settings, ThreadPool threadPool,
            BigArrays bigArrays, CircuitBreakerService circuitBreakerService,
            NamedWriteableRegistry namedWriteableRegistry, NamedXContentRegistry namedXContentRegistry,
            NetworkService networkService, Dispatcher dispatcher) {
        Map<String, Supplier<HttpServerTransport>> transports = sgPlugin.getHttpTransports(settings, threadPool, bigArrays, circuitBreakerService,
                namedWriteableRegistry, namedXContentRegistry, networkService, dispatcher);
        return transports;
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> getGuiceServiceClasses() {
        Collection<Class<? extends LifecycleComponent>> serviceClasses = sgPlugin.getGuiceServiceClasses();
        return serviceClasses;
    }

    @Override
    public Settings additionalSettings() {
        return Settings.builder()
                .put(sgPlugin.additionalSettings())
                .build();

    }

    @Override
    public List<Setting<?>> getSettings() {
        List<Setting<?>> settings = sgPlugin.getSettings();
        settings.add(Setting.simpleString(OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_APP, Property.NodeScope));
        settings.add(Setting.simpleString(OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_OPERATIONS, Property.NodeScope));
        settings.add(Setting.simpleString(OPENSHIFT_ES_KIBANA_SEED_MAPPINGS_EMPTY, Property.NodeScope));
        settings.add(Setting.boolSetting("openshift.config.use_common_data_model", true, Property.NodeScope));
        settings.add(Setting.simpleString(OPENSHIFT_CONFIG_PROJECT_INDEX_PREFIX, Property.NodeScope));
        settings.add(Setting.simpleString("openshift.config.time_field_name", Property.NodeScope));
        settings.add(Setting.simpleString("openshift.searchguard.keystore.path", Property.NodeScope));
        settings.add(Setting.simpleString("openshift.searchguard.truststore.path", Property.NodeScope));
        settings.add(Setting.boolSetting("openshift.operations.allow_cluster_reader", false, Property.NodeScope));
        settings.add(Setting.simpleString("openshift.kibana.index.mode", Property.NodeScope));
        settings.add(Setting.simpleString(OPENSHIFT_ACL_ROLE_STRATEGY, Property.NodeScope));
        settings.add(Setting.listSetting(OPENSHIFT_KIBANA_OPS_INDEX_PATTERNS, Arrays.asList(DEFAULT_KIBANA_OPS_INDEX_PATTERNS), 
                Function.identity(), Property.NodeScope, Property.Dynamic));
            
        return settings;
    }

    @Override
    public List<String> getSettingsFilter() {
        List<String> filter = sgPlugin.getSettingsFilter();
        filter.add("openshift.*");
        filter.add("io.fabric8.elasticsearch.*");
        return filter;
    }

    @Override
    public void close() throws IOException {
        if (sgPlugin != null) {
            sgPlugin.close();
        }
        super.close();
    }

}
