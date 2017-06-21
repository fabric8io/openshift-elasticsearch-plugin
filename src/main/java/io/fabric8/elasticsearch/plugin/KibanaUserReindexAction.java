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
import java.nio.ByteBuffer;
import java.util.Map;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.ActionWriteResponse.ShardInfo;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.get.MultiGetResponse.Failure;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.tasks.Task;

import com.google.common.collect.ImmutableMap;

import io.fabric8.elasticsearch.plugin.kibana.GetResultBuilder;

public class KibanaUserReindexAction implements ActionFilter, ConfigurationSettings {

    private final ESLogger logger;
    private final String kibanaIndex;

    private Boolean enabled;

    @Inject
    public KibanaUserReindexAction(final Settings settings, final ClusterService clusterService, final Client client) {
        this.enabled = settings.getAsBoolean(OPENSHIFT_KIBANA_REWRITE_ENABLED_FLAG,
                OPENSHIFT_KIBANA_REWRITE_ENABLED_DEFAULT);
        this.logger = Loggers.getLogger(KibanaUserReindexAction.class);
        this.kibanaIndex = settings.get(KIBANA_CONFIG_INDEX_NAME, DEFAULT_USER_PROFILE_PREFIX);

        if (enabled) {
            logger.debug("Initializing KibanaUserReindexAction");
        }
    }

    @Override
    public int order() {
        // We want this to be the last in the chain
        return Integer.MAX_VALUE;
    }

    @Override
    public void apply(Task task, String action, ActionRequest request, ActionListener listener,
            ActionFilterChain chain) {
        chain.proceed(task, action, request, listener);
    }

    @Override
    public void apply(String action, ActionResponse response, @SuppressWarnings("rawtypes") ActionListener listener,
            ActionFilterChain chain) {

        if (enabled) {
            logger.debug("Response with Action '{}' and class '{}'", action, response.getClass());

            if (containsKibanaUserIndex(response)) {

                if (response instanceof IndexResponse) {
                    final IndexResponse ir = (IndexResponse) response;

                    String index = getIndex(ir);
                    ShardInfo shardInfo = ir.getShardInfo();

                    response = new IndexResponse(index, ir.getType(), ir.getId(), ir.getVersion(), ir.isCreated());
                    ((IndexResponse) response).setShardInfo(shardInfo);
                } else if (response instanceof GetResponse) {
                    response = new GetResponse(buildNewResult((GetResponse) response));
                } else if (response instanceof DeleteResponse) {
                    final DeleteResponse dr = (DeleteResponse) response;
                    String index = getIndex(dr);
                    ShardInfo shardInfo = dr.getShardInfo();

                    response = new DeleteResponse(index, dr.getType(), dr.getId(), dr.getVersion(), dr.isFound());
                    ((DeleteResponse) response).setShardInfo(shardInfo);
                } else if (response instanceof MultiGetResponse) {
                    final MultiGetResponse mgr = (MultiGetResponse) response;

                    MultiGetItemResponse[] responses = new MultiGetItemResponse[mgr.getResponses().length];
                    int index = 0;

                    for (MultiGetItemResponse item : mgr.getResponses()) {

                        GetResponse itemResponse = item.getResponse();
                        Failure itemFailure = item.getFailure();

                        GetResponse getResponse = (itemResponse != null) ? new GetResponse(buildNewResult(itemResponse))
                                : null;
                        Failure failure = (itemFailure != null) ? buildNewFailure(itemFailure) : null;

                        responses[index] = new MultiGetItemResponse(getResponse, failure);
                        index++;
                    }

                    response = new MultiGetResponse(responses);
                } else if (response instanceof GetFieldMappingsResponse) {
                    final GetFieldMappingsResponse gfmResponse = (GetFieldMappingsResponse) response;
                    ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings = gfmResponse
                            .mappings();

                    String index = "";
                    for (String key : mappings.keySet()) {

                        index = key;
                        if (isKibanaUserIndex(index)) {
                            index = kibanaIndex;
                        }
                    }

                    BytesStreamOutput bso = new BytesStreamOutput();
                    try {

                        MappingResponseRemapper remapper = new MappingResponseRemapper();
                        remapper.updateMappingResponse(bso, index, mappings);

                        ByteBuffer buffer = ByteBuffer.wrap(bso.bytes().toBytes());
                        ByteBufferStreamInput input = new ByteBufferStreamInput(buffer);

                        response.readFrom(input);
                    } catch (IOException e) {
                        logger.error("Error while rewriting GetFieldMappingsResponse", e);
                    }
                }
            }
        }

        chain.proceed(action, response, listener);
    }

    private GetResult buildNewResult(GetResponse response) {
        String index = getIndex(response);
        String replacedIndex = response.getIndex();
        return new GetResultBuilder().index(index).replacedIndex(replacedIndex).response(response).build();
    }

    private Failure buildNewFailure(Failure failure) {
        String index = failure.getIndex();
        String message = failure.getMessage();

        if (isKibanaUserIndex(index)) {
            message = message.replace(index, kibanaIndex);
            index = kibanaIndex;
        }

        Throwable t = new Throwable(message, failure.getFailure().getCause());

        return new Failure(index, failure.getType(), failure.getId(), t);
    }

    private boolean isKibanaUserIndex(String index) {
        return (index.startsWith(kibanaIndex) && !index.equalsIgnoreCase(kibanaIndex));
    }

    private String getIndex(ActionResponse response) {
        String index = "";

        if (response instanceof IndexResponse) {
            index = ((IndexResponse) response).getIndex();
        } else if (response instanceof GetResponse) {
            index = ((GetResponse) response).getIndex();
        } else if (response instanceof DeleteResponse) {
            index = ((DeleteResponse) response).getIndex();
        }

        if (isKibanaUserIndex(index)) {
            index = kibanaIndex;
        }

        return index;
    }

    private boolean containsKibanaUserIndex(ActionResponse response) {
        String index = "";

        if (response instanceof MultiGetResponse) {
            for (MultiGetItemResponse item : ((MultiGetResponse) response).getResponses()) {
                GetResponse itemResponse = item.getResponse();
                Failure itemFailure = item.getFailure();

                if (itemResponse == null) {
                    if (isKibanaUserIndex(itemFailure.getIndex())) {
                        return true;
                    }
                } else {
                    if (isKibanaUserIndex(itemResponse.getIndex())) {
                        return true;
                    }
                }
            }

            return false;
        }

        if (response instanceof IndexResponse) {
            index = ((IndexResponse) response).getIndex();
        } else if (response instanceof GetResponse) {
            index = ((GetResponse) response).getIndex();
        } else if (response instanceof DeleteResponse) {
            index = ((DeleteResponse) response).getIndex();
        } else if (response instanceof GetFieldMappingsResponse) {
            ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings = ((GetFieldMappingsResponse) response)
                    .mappings();
            for (String key : mappings.keySet()) {
                index = key;
            }
        }

        return isKibanaUserIndex(index);
    }

    /*
     * Courtesy of GetFieldMappingsResponse.writeTo
     */
    private static class MappingResponseRemapper extends ActionResponse implements ToXContent {

        ESLogger logger = Loggers.getLogger(MappingResponseRemapper.class);

        public void updateMappingResponse(StreamOutput out, String index,
                ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings)
                throws IOException {
            super.writeTo(out);
            out.writeVInt(mappings.size());
            for (Map.Entry<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> indexEntry : mappings
                    .entrySet()) {
                out.writeString(index);
                out.writeVInt(indexEntry.getValue().size());
                for (Map.Entry<String, ImmutableMap<String, FieldMappingMetaData>> typeEntry : indexEntry.getValue()
                        .entrySet()) {
                    out.writeString(typeEntry.getKey());
                    out.writeVInt(typeEntry.getValue().size());

                    for (Map.Entry<String, FieldMappingMetaData> fieldEntry : typeEntry.getValue().entrySet()) {
                        out.writeString(fieldEntry.getKey());
                        FieldMappingMetaData fieldMapping = fieldEntry.getValue();
                        out.writeString(fieldMapping.fullName());

                        // below replaces logic of
                        // out.writeBytesReference(fieldMapping.source);
                        Map<String, Object> map = fieldMapping.sourceAsMap();

                        XContentBuilder builder = XContentBuilder.builder(JsonXContent.jsonXContent);

                        builder.map(map).close();
                        out.writeBytesReference(builder.bytes());
                    }
                }
            }
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            return null;
        }
    }
}
