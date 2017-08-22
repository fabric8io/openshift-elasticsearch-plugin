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
import java.util.Map.Entry;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.get.MultiGetResponse.Failure;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.ByteBufferStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.get.GetResult;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.tasks.Task;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.kibana.GetResultBuilder;

//TODO: This should be combinable with KibanaUserReindexFilter
@SuppressWarnings("rawtypes")
public class KibanaUserReindexAction implements ActionFilter, ConfigurationSettings {

    private static final Logger LOG = Loggers.getLogger(KibanaUserReindexAction.class);
    private final String defaultKibanaIndex;
    private final ThreadContext threadContext;

    public KibanaUserReindexAction(final PluginSettings settings, final Client client, final ThreadContext threadContext) {
        this.defaultKibanaIndex = settings.getDefaultKibanaIndex();
        this.threadContext = threadContext;
    }

    @Override
    public int order() {
        // We want this to be the last in the chain
        return Integer.MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void apply(Task task, String action, final ActionRequest request, final ActionListener listener,
            ActionFilterChain chain) {
        LOG.debug("Response with Action '{}'", action);
        chain.proceed(task, action, request, new ActionListener<ActionResponse>() {

            @Override
            public void onResponse(ActionResponse response) {
                final OpenshiftRequestContext context = threadContext.getTransient(OPENSHIFT_REQUEST_CONTEXT);
                if (containsKibanaUserIndex(response)) {
                    if (response instanceof IndexResponse) {
                        final IndexResponse ir = (IndexResponse) response;

                        String index = getIndex(ir);
                        ShardId shardId = ir.getShardId();

                        IndexResponse.Builder builder = new IndexResponse.Builder();
                        builder.setShardId(new ShardId(index, shardId.getIndex().getUUID(), shardId.id()));
                        builder.setType(ir.getType());
                        builder.setId(ir.getId());
                        builder.setVersion(ir.getVersion());
                        builder.setResult(ir.getResult());
                        builder.setShardInfo(ir.getShardInfo());
                        response = builder.build();
                    } else if (response instanceof GetResponse) {
                        response = new GetResponse(buildNewResult((GetResponse) response));
                    } else if (response instanceof DeleteResponse) {
                        final DeleteResponse dr = (DeleteResponse) response;
                        String index = getIndex(dr);
                        ShardId shardId = dr.getShardId();
                        ShardInfo shardInfo = dr.getShardInfo();

                        ShardId replacedShardId = new ShardId(index, shardId.getIndex().getUUID(), shardId.id());
                        response = new DeleteResponse(replacedShardId, dr.getType(), dr.getId(), dr.getVersion(),
                                RestStatus.NOT_FOUND != dr.status());
                        ((DeleteResponse) response).setShardInfo(shardInfo);
                    } else if (response instanceof MultiGetResponse) {
                        final MultiGetResponse mgr = (MultiGetResponse) response;

                        MultiGetItemResponse[] responses = new MultiGetItemResponse[mgr.getResponses().length];
                        int index = 0;

                        for (MultiGetItemResponse item : mgr.getResponses()) {

                            GetResponse itemResponse = item.getResponse();
                            Failure itemFailure = item.getFailure();

                            GetResponse getResponse = (itemResponse != null)
                                    ? new GetResponse(buildNewResult(itemResponse))
                                    : null;
                            Failure failure = (itemFailure != null) ? buildNewFailure(itemFailure) : null;

                            responses[index] = new MultiGetItemResponse(getResponse, failure);
                            index++;
                        }

                        response = new MultiGetResponse(responses);
                    } else if (response instanceof GetFieldMappingsResponse) {
                        final GetFieldMappingsResponse gfmResponse = (GetFieldMappingsResponse) response;
                        Map<String, Map<String, Map<String, FieldMappingMetaData>>> mappings = gfmResponse
                                .mappings();

                        String index = "";
                        for (String key : mappings.keySet()) {

                            index = key;
                            if (isKibanaUserIndex(index)) {
                                index = defaultKibanaIndex;
                            }
                        }

                        BytesStreamOutput bso = new BytesStreamOutput();
                        try {

                            MappingResponseRemapper remapper = new MappingResponseRemapper();
                            remapper.updateMappingResponse(bso, index, mappings);

                            ByteBuffer buffer = ByteBuffer.wrap(bso.bytes().toBytesRef().bytes);
                            ByteBufferStreamInput input = new ByteBufferStreamInput(buffer);

                            response.readFrom(input);
                        } catch (IOException e) {
                            LOG.error("Error while rewriting GetFieldMappingsResponse", e);
                        }
                    } else if (response instanceof SearchResponse && context != null) {
                        String json = Strings.toString((SearchResponse)response);
                        json = json.replaceAll("_index\".?:.?\"" + context.getKibanaIndex() + "\"", "_index\":\"" + defaultKibanaIndex + "\"");
                        LOG.debug("Modified SearchResponse to {}", json);
                        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(NamedXContentRegistry.EMPTY, json)) {
                            response = SearchResponse.fromXContent(parser);
                        } catch (IOException e) {
                            LOG.error("Error trying to modify kibana index response", e);
                        }
                    }
                }
                listener.onResponse(response);
            }

            @Override
            public void onFailure(Exception e) {
                listener.onFailure(e);

            }
        });
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
            message = message.replace(index, defaultKibanaIndex);
            index = defaultKibanaIndex;
        }

        Exception t = new Exception(message, failure.getFailure().getCause());

        return new Failure(index, failure.getType(), failure.getId(), t);
    }

    private boolean isKibanaUserIndex(String index) {
        return (index.startsWith(defaultKibanaIndex) && !index.equalsIgnoreCase(defaultKibanaIndex));
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
            index = defaultKibanaIndex;
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
        
        if (response instanceof SearchResponse) {
            SearchResponse search = (SearchResponse) response;
            for (SearchHit hit : search.getHits()) {
                if (isKibanaUserIndex(hit.getIndex())) {
                    return true;
                }
            }
        }

        if (response instanceof IndexResponse) {
            index = ((IndexResponse) response).getIndex();
        } else if (response instanceof GetResponse) {
            index = ((GetResponse) response).getIndex();
        } else if (response instanceof DeleteResponse) {
            index = ((DeleteResponse) response).getIndex();
        } else if (response instanceof GetFieldMappingsResponse) {
            Map<String, Map<String, Map<String, FieldMappingMetaData>>> mappings = ((GetFieldMappingsResponse) response)
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

        public void updateMappingResponse(StreamOutput out, String index,
                Map<String, Map<String, Map<String, FieldMappingMetaData>>> mappings)
                throws IOException {
            super.writeTo(out);
            out.writeVInt(mappings.size());
            for (Entry<String, Map<String, Map<String, FieldMappingMetaData>>> indexEntry : mappings
                    .entrySet()) {
                out.writeString(index);
                out.writeVInt(indexEntry.getValue().size());
                for (Map.Entry<String, Map<String, FieldMappingMetaData>> typeEntry : indexEntry.getValue()
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
