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
package io.fabric8.elasticsearch.plugin.kibana;

import org.apache.commons.lang.StringEscapeUtils;

public class DocumentBuilder {
	
	private final String OPERATIONS_FIELDS = "[{\"name\":\"_index\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"ident\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"_type\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":false},{\"name\":\"pid\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"message\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"version\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"hostname\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"_source\",\"type\":\"_source\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"docker_container_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_component\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_openshift_io/build_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_deploymentconfig\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_pod_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_namespace_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_deployment\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_container_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_pod_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_provider\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_openshift_io/deployer-pod-for_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"time\",\"type\":\"date\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":false},{\"name\":\"kubernetes_host\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_docker-registry\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false}]";
	private final String APPLICATION_FIELDS = "[{\"name\":\"_index\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"kubernetes_labels_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"docker_container_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"hostname\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_openshift_io/build_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"_type\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":false},{\"name\":\"kubernetes_labels_deploymentconfig\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"message\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"version\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_pod_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_namespace_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_deployment\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_container_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_pod_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"kubernetes_labels_openshift_io/deployer-pod-for_name\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false},{\"name\":\"_source\",\"type\":\"_source\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"time\",\"type\":\"date\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":false},{\"name\":\"kubernetes_host\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":true,\"doc_values\":false}]";
	private final String BLANK_FIELDS = "[{\"name\":\"_index\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_type\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_source\",\"type\":\"_source\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"_id\",\"type\":\"string\",\"count\":0,\"scripted\":false,\"indexed\":false,\"analyzed\":false,\"doc_values\":false},{\"name\":\"time\",\"type\":\"date\",\"count\":0,\"scripted\":false,\"indexed\":true,\"analyzed\":false,\"doc_values\":false}]";
	private final String SEARCH_FIELDS = "{\"properties\":{\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"hits\":{\"type\":\"integer\"},\"columns\":{\"type\":\"string\"},\"sort\":{\"type\":\"string\"},\"version\":{\"type\":\"integer\"},\"kibanaSavedObjectMeta\":{\"properties\":{\"searchSourceJSON\":{\"type\":\"string\"}}}}}";
	
	private StringBuffer contents;
	private boolean containsFields;

	public DocumentBuilder() {
		contents = new StringBuffer();
		containsFields = false;
		contents.append('{');
	}
	
	public DocumentBuilder title(String title) {
		return addField("title", title);
	}
	
	public DocumentBuilder description(String description) {
		return addField("description", description);
	}
	
	public DocumentBuilder version(int value) {
		return addField("version", value);
	}
	
	
	public DocumentBuilder timeFieldName(String name) {
		return addField("timeFieldName", name);
	}
	
	public DocumentBuilder intervalName(String name) {
		return addField("intervalName", name);
	}
	
	public DocumentBuilder applicationFields(boolean use_cdm) {
		if ( use_cdm )
			return this; // common data model uses index templates, so these are not needed
		else
			return addField("fields", StringEscapeUtils.escapeJava(APPLICATION_FIELDS));
	}
	
	public DocumentBuilder operationsFields(boolean use_cdm) {
		if ( use_cdm )
			return this; // common data model uses index templates, so these are not needed
		else
			return addField("fields", StringEscapeUtils.escapeJava(OPERATIONS_FIELDS));
	}

	public DocumentBuilder blankFields() {
		return addField("fields", StringEscapeUtils.escapeJava(BLANK_FIELDS));
	}
	
	public DocumentBuilder searchProperty() {
		
		addComma();
		
		contents.append("\"properties\":");
		contents.append(StringEscapeUtils.escapeJava(SEARCH_FIELDS));
		
		if ( !containsFields )
			containsFields = true;
		
		return this;
	}
	
	public DocumentBuilder defaultIndex(String index) {
		return addField(KibanaSeed.DEFAULT_INDEX_FIELD, index);
	}
	
	private DocumentBuilder addField(String key, int value) {
		
		addComma();
		
		contents.append('"');
		contents.append(key);
		contents.append("\":");
		contents.append(value);
		
		if ( !containsFields )
			containsFields = true;
		
		return this;
	}
	
	private DocumentBuilder addField(String key, String value) {

		addComma();
		contents.append('"');
		contents.append(key);
		contents.append("\":\"");
		contents.append(value);
		contents.append('"');
		
		if ( !containsFields )
			containsFields = true;
		
		return this;
	}
	
	private void addComma() {
		if ( containsFields )
			contents.append(',');
	}
	
	public String build() {
		contents.append('}');
		return contents.toString();
	}
}
