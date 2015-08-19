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

import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.openshift.api.model.PolicyBinding;
import io.fabric8.openshift.api.model.PolicyBindingList;
import io.fabric8.openshift.client.DefaultOpenshiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.LocalNodeMasterListener;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.concurrent.atomic.AtomicReference;

public class OpenShiftSearchGuardSyncService extends AbstractLifecycleComponent<OpenShiftSearchGuardSyncService> implements LocalNodeMasterListener {

  private static final String SETTINGS_PREFIX = "os.sg.sync.";

  private final AtomicReference<String> policyBindingsResourceVersion = new AtomicReference<>();
  private final ClusterService clusterService;

  private OpenShiftClient osClient;
  private Watch watch;

  @Inject
  protected OpenShiftSearchGuardSyncService(Settings settings, ClusterService clusterService) {
    super(settings);

    this.osClient = new DefaultOpenshiftClient();
    this.clusterService = clusterService;

    logger.info("Initialized {}", getClass().getSimpleName());
  }

  @Override
  protected void doStart() throws ElasticsearchException {
    clusterService.add(this);
  }

  @Override
  protected void doStop() throws ElasticsearchException {
    logger.debug("Stopping watching OpenShift policy bindings");
    if (watch != null && watch.isOpen()) {
      watch.close();
    }
    logger.debug("Stopped watching OpenShift policy bindings");
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
    logger.debug("Starting watching OpenShift policy bindings");
    PolicyBindingList policyBindings = osClient.policyBindings().list();

    policyBindingsResourceVersion.set(policyBindings.getMetadata().getResourceVersion());

    watch = osClient.policyBindings().watch(policyBindings.getMetadata().getResourceVersion(), new Watcher<PolicyBinding>() {
      @Override
      public void eventReceived(Action action, PolicyBinding policyBinding) {
        logger.trace("Received {}: {}", action, policyBinding);

        policyBindingsResourceVersion.set(policyBinding.getMetadata().getResourceVersion());
      }
    });
    logger.debug("Started watching OpenShift policy bindings");
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
}
