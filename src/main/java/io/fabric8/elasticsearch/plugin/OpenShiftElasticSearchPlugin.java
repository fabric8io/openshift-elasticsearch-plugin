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

import java.io.Closeable;
import java.util.Collection;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.http.HttpServerModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;
import org.elasticsearch.transport.TransportModule;

import com.floragunn.searchguard.SearchGuardPlugin;
import com.floragunn.searchguard.ssl.SearchGuardSSLPlugin;
import com.google.common.collect.Lists;

import io.fabric8.elasticsearch.plugin.filter.FieldStatsResponseFilter;
import io.fabric8.elasticsearch.rest.KibanaUserRestHandler;

public class OpenShiftElasticSearchPlugin extends Plugin implements ConfigurationSettings {

    private final Settings settings;
    private final SearchGuardPlugin searchguard;
    private final SearchGuardSSLPlugin sgSSL;

    @Inject
    public OpenShiftElasticSearchPlugin(final Settings settings) {
        // This is to not have SG print an error with us loading the SG_SSL
        // plugin for our transport client
        System.setProperty("sg.nowarn.client", "true");
        this.settings = settings;//Settings.builder().put(settings).build();
        this.sgSSL = new SearchGuardSSLPlugin(this.settings);
        this.searchguard = new SearchGuardPlugin(this.settings);
    }

    @Override
    public String name() {
        return "openshift-elasticsearch-plugin";
    }

    @Override
    public String description() {
        return "OpenShift ElasticSearch Plugin";
    }

    public void onModule(ActionModule actionModule) {
        actionModule.registerFilter(FieldStatsResponseFilter.class);
        actionModule.registerFilter(KibanaUserReindexAction.class);
        searchguard.onModule(actionModule);
    }

    public void onModule(RestModule restModule) {
        searchguard.onModule(restModule);
        sgSSL.onModule(restModule);
        restModule.addRestAction(KibanaUserRestHandler.class);
    }
    
    public void onModule(final TransportModule module) {
        searchguard.onModule(module);
        sgSSL.onModule(module);
    }
    
    public void onModule(final HttpServerModule module) {
        searchguard.onModule(module);
        sgSSL.onModule(module);
    }

    @Override
    public Collection<Module> shardModules(Settings indexSettings) {
        Collection<Module> modules = Lists.newArrayList();
        modules.addAll(searchguard.shardModules(indexSettings));
        modules.addAll(sgSSL.shardModules(indexSettings));
        return modules;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        Collection<Class<? extends LifecycleComponent>> services = Lists.newArrayList();
        services.addAll(searchguard.nodeServices());
        services.addAll(sgSSL.nodeServices());
        services.add(OpenShiftElasticSearchService.class);
        return services;
    }

    @Override
    public Collection<Module> nodeModules() {
        Collection<Module> modules = Lists.newArrayList();
        modules.addAll(searchguard.nodeModules());
        modules.addAll(sgSSL.nodeModules());
        modules.add(new OpenShiftElasticSearchModule());
        return modules;
    }
    

    @Override
    public Collection<Module> indexModules(Settings indexSettings) {
        Collection<Module> modules = Lists.newArrayList();
        modules.addAll(searchguard.indexModules(indexSettings));
        modules.addAll(sgSSL.indexModules(indexSettings));
        return modules;
    }

    @Override
    public Collection<Class<? extends Closeable>> indexServices() {
        Collection<Class<? extends Closeable>> services = Lists.newArrayList();
        services.addAll(searchguard.indexServices());
        services.addAll(sgSSL.indexServices());
        return services;
    }

    @Override
    public Collection<Class<? extends Closeable>> shardServices() {
        Collection<Class<? extends Closeable>> services = Lists.newArrayList();
        services.addAll(searchguard.shardServices());
        services.addAll(sgSSL.shardServices());
        return services;
    }

    @Override
    public Settings additionalSettings() {
        Settings.Builder settingsBuilder = Settings.builder()
                .put(settings)
                .put(searchguard.additionalSettings())
                .put(sgSSL.additionalSettings());
        return settingsBuilder.build();
    }
}
