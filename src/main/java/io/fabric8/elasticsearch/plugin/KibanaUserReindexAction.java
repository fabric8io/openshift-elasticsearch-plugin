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
import java.util.Map;

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
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.ImmutableMap;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.get.GetResult;

public class KibanaUserReindexAction implements ActionFilter, ConfigurationSettings {

	private final ESLogger logger;
	private final String kibanaIndex;
	
	@Inject
	public KibanaUserReindexAction(final Settings settings, final ClusterService clusterService, final Client client) {
		this.logger = Loggers.getLogger(KibanaUserReindexAction.class);
		this.kibanaIndex = settings.get(KIBANA_CONFIG_INDEX_NAME, DEFAULT_USER_PROFILE_PREFIX);
		
		logger.debug("Initializing KibanaUserReindexAction");
	}

	@Override
	public int order() {
		// We want this to be the last in the chain
		return Integer.MAX_VALUE;
	}

	@Override
	public void apply(String action, ActionRequest request,
			ActionListener listener, ActionFilterChain chain) {
		chain.proceed(action, request, listener);
	}

	@Override
	public void apply(String action, ActionResponse response,
			ActionListener listener, ActionFilterChain chain) {
		
		logger.debug("Response with Action '{}' and class '{}'", action, response.getClass());
		
		if ( containsKibanaUserIndex(response) ) {
		
			if ( response instanceof IndexResponse ) {
				final IndexResponse ir = (IndexResponse) response;
				String index = getIndex(ir);
				
				response = new IndexResponse(index, ir.getType(), ir.getId(), ir.getVersion(), ir.isCreated());
			}
			else if ( response instanceof GetResponse ) {
				response = new GetResponse(buildNewResult((GetResponse) response));
			}
			else if ( response instanceof DeleteResponse ) {
				final DeleteResponse dr = (DeleteResponse) response;
				String index = getIndex(dr);
				
				response = new DeleteResponse(index, dr.getType(), dr.getId(), dr.getVersion(), dr.isFound());
			}
			else if ( response instanceof MultiGetResponse ) {
				final MultiGetResponse mgr = (MultiGetResponse) response;
				
				MultiGetItemResponse[] responses = new MultiGetItemResponse[mgr.getResponses().length];
				int index = 0;
				
				for ( MultiGetItemResponse item : mgr.getResponses() ) {
					GetResponse itemResponse = item.getResponse();
					Failure itemFailure = item.getFailure();
					
					GetResponse getResponse = (itemResponse != null) ? new GetResponse(buildNewResult(itemResponse)) : null;
					Failure failure = (itemFailure != null) ? buildNewFailure(itemFailure) : null;
	
					responses[index] = new MultiGetItemResponse(getResponse, failure);
					index++;
				}
				
				response = new MultiGetResponse(responses);
			}
			else if ( response instanceof GetFieldMappingsResponse ) {
				final GetFieldMappingsResponse gfmResponse = (GetFieldMappingsResponse) response;
				
				ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings = gfmResponse.mappings();
				
				//String index = ((Map.Entry<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>>)mappings).getKey();
				String index = "";
				for ( String key : mappings.keySet() ) {
				
					index = key;
					if ( isKibanaUserIndex(index) ) {
						index = kibanaIndex;
					}
				}
				
				BytesStreamOutput bso = new BytesStreamOutput();
				try {
	
					MappingResponseRemapper remapper = new MappingResponseRemapper();
					remapper.updateMappingResponse(bso, index, mappings);
					
					BytesStreamInput input = new BytesStreamInput(bso.bytes());
	
					response.readFrom(input);
				} catch (IOException e) {
					logger.error("Error while rewriting GetFieldMappingsResponse", e);
				}
			}
		}
		
		chain.proceed(action, response, listener);
	}
	
	private GetResult buildNewResult(GetResponse response) {
		String index = getIndex(response);
		String replacedIndex = response.getIndex();
		
		//update this to check for .kibana.* in the source
		BytesReference replacedContent = null;
		if ( ! response.isSourceEmpty() ) {
			String source = response.getSourceAsBytesRef().toUtf8();
			String replaced = source.replaceAll(replacedIndex, index);
			replacedContent = new BytesArray(replaced);
		}
		
		//Check for .kibana.* in the fields
		Map<String, GetField> responseFields = response.getFields();
		for ( String key : responseFields.keySet() ) {
			
			GetField replacedField = responseFields.get(key);
			
			for ( Object o : replacedField.getValues() ) {
				if ( o instanceof String ) {
					String value = (String) o;
					
					if ( value.contains(replacedIndex) ) {
						replacedField.getValues().remove(o);
						replacedField.getValues().add(value.replaceAll(replacedIndex, index));
					}
				}
			}
			
		}
		
		GetResult getResult = new GetResult(index, response.getType(), response.getId(), response.getVersion(),
				response.isExists(), 
				replacedContent, 
				response.getFields());
		
		return getResult;
	}
	
	private Failure buildNewFailure(Failure failure) {
		String index = failure.getIndex();
		String message = failure.getMessage();
		
		if ( isKibanaUserIndex(index) ) {
			message = message.replace(index, kibanaIndex);
			index = kibanaIndex;
		}
		
		return new Failure(index, failure.getType(), failure.getId(), message);
	}

	private boolean isKibanaUserIndex(String index) {
		return (index.startsWith(kibanaIndex) && !index.equalsIgnoreCase(kibanaIndex));
	}
	
	private String getIndex(ActionResponse response) {
		String index = "";
		
		if ( response instanceof IndexResponse )
			index = ((IndexResponse) response).getIndex();
		else if ( response instanceof GetResponse )
			index = ((GetResponse)response).getIndex();
		else if ( response instanceof DeleteResponse )
			index = ((DeleteResponse)response).getIndex();
		
		if ( isKibanaUserIndex(index) ) {
			index = kibanaIndex;
		}
		
		return index;
	}
	
	private boolean containsKibanaUserIndex(ActionResponse response) {
		String index = "";
		
		if ( response instanceof MultiGetResponse ) {
			for ( MultiGetItemResponse item : ((MultiGetResponse)response).getResponses() ) {
				GetResponse itemResponse = item.getResponse();
				Failure itemFailure = item.getFailure();
			
				if ( itemResponse == null ) {
					if ( isKibanaUserIndex(itemFailure.getIndex()) )
						return true;
				}
				else {
					if ( isKibanaUserIndex(itemResponse.getIndex()) )
						return true;
				}
			}
			
			return false;
		}
		
		if ( response instanceof IndexResponse )
			index = ((IndexResponse) response).getIndex();
		else if ( response instanceof GetResponse )
			index = ((GetResponse)response).getIndex();
		else if ( response instanceof DeleteResponse )
			index = ((DeleteResponse)response).getIndex();
		else if ( response instanceof GetFieldMappingsResponse) {
			ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings = ((GetFieldMappingsResponse)response).mappings();
			for ( String key : mappings.keySet() )
				index = key;
		}
		
		return isKibanaUserIndex(index);
	}
	
	/*
	 * Courtesy of GetFieldMappingsResponse.writeTo
	 */
	private static class MappingResponseRemapper extends ActionResponse implements ToXContent {
		
		ESLogger logger = Loggers.getLogger(MappingResponseRemapper.class);
		
		public void updateMappingResponse(StreamOutput out, String index, ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings) throws IOException {
			super.writeTo(out);
			out.writeVInt(mappings.size());
	        for (Map.Entry<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> indexEntry : mappings.entrySet()) {
	            out.writeString(index);
	            out.writeVInt(indexEntry.getValue().size());
	            for (Map.Entry<String, ImmutableMap<String, FieldMappingMetaData>> typeEntry : indexEntry.getValue().entrySet()) {
	                out.writeString(typeEntry.getKey());
	                out.writeVInt(typeEntry.getValue().size());
	                for (Map.Entry<String, FieldMappingMetaData> fieldEntry : typeEntry.getValue().entrySet()) {
	                    out.writeString(fieldEntry.getKey());
	                    FieldMappingMetaData fieldMapping = fieldEntry.getValue();
	                    out.writeString(fieldMapping.fullName());
	                    
	                    Map<String, Object> map = fieldMapping.sourceAsMap();
	                    
	                    StringBuffer buffer = new StringBuffer();
	                    addMapToBuffer(buffer, map);
	                    
	                    logger.debug("==== Built JSON buffer value of '{}' ====", buffer.toString());
	                    out.writeBytesReference(new BytesArray(buffer.toString()));
	                }
	            }
	        }
		}
		
		private void addMapToBuffer(StringBuffer buffer, Map<String, Object> map) throws IOException {
			buffer.append('{');
    		
    		boolean added = false;
    		for ( String key : map.keySet() ) {
    			
    			if ( added )
    				buffer.append(',');
    			
    			buffer.append('"');
    			buffer.append(key);
    			buffer.append("\":");
    			
    			Object value = map.get(key);
    			if ( value instanceof String ) {
    				buffer.append('"');
    				buffer.append(value);
    				buffer.append('"');
    			}
    			else if ( value instanceof Map ) {
    				//recursion!
    				addMapToBuffer(buffer, (Map<String, Object>)value);
    			}
    			/*else if ( value instanceof GetFieldMappingsResponse ) {
    				ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings 
    						= ((GetFieldMappingsResponse)value).mappings();
    				
    				addImmutableMapToBuffer(buffer, mappings);
    			}*/
    			else {
    				buffer.append(value);
    			}
    			
    			if ( !added )
    				added = true;
    		}
    		
    		buffer.append('}');	
		}
		
		private void addImmutableMapToBuffer(StringBuffer buffer, ImmutableMap<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> mappings) throws IOException {
			buffer.append('{');

	        for (Map.Entry<String, ImmutableMap<String, ImmutableMap<String, FieldMappingMetaData>>> indexEntry : mappings.entrySet()) {
	            
	        	buffer.append('"');
	            buffer.append(indexEntry.getKey());
	        	buffer.append("\":{");
	            
	            for (Map.Entry<String, ImmutableMap<String, FieldMappingMetaData>> typeEntry : indexEntry.getValue().entrySet()) {
	                
	            	buffer.append('"');
	            	buffer.append(typeEntry.getKey());
	            	buffer.append("\":{");

	                for (Map.Entry<String, FieldMappingMetaData> fieldEntry : typeEntry.getValue().entrySet()) {
	                	
	                	buffer.append('"');
	                	buffer.append(fieldEntry.getKey());
	                	buffer.append("\":{");
	                    //out.writeString(fieldEntry.getKey());
	                    FieldMappingMetaData fieldMapping = fieldEntry.getValue();
	                    //out.writeString(fieldMapping.fullName());
	                    buffer.append('"');
	                    buffer.append(fieldMapping.fullName());
	                    buffer.append("\":");
	                    
	                    Map<String, Object> map = fieldMapping.sourceAsMap();
	                    
	                    addMapToBuffer(buffer, map);
	                }
	            }
	        }
	        
	        buffer.append('}');
		}

		@Override
		public XContentBuilder toXContent(XContentBuilder builder, Params params)
				throws IOException {
			return null;
		}
	}
	
}
