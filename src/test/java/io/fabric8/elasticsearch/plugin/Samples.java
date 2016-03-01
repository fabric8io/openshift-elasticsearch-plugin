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
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public enum Samples {
	
	ACL("searchguard_acl.json"),
	OPENSHIFT_ACL("searchguard_acl_with_openshift_projects.json"),
	CONFIG_ACL("elasticsearch_test_config.yaml"),
	CONFIG_EXPECTED_ACL("elasticsearch_test_expected.json");

	private String path;
	
	Samples(String path){
		this.path = path;
	}
	
	public String getContent(){
			InputStream is = Samples.class.getResourceAsStream(path);
			try {
				return IOUtils.toString(is, "UTF-8");
			} catch (IOException e) {
				throw new RuntimeException(String.format("Unable to read file {}", path), e);
			}
	}
}
