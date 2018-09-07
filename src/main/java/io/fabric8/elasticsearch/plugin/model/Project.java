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

package io.fabric8.elasticsearch.plugin.model;

import org.apache.commons.lang.ObjectUtils;

/**
 * Simple model to represent an OpenShift project
 *
 */
public class Project implements Comparable<Project> {

    public static final Project EMPTY = new Project("","");
    private final String name;
    private final String uid;
    
    public Project(String name, String uid) {
        this.name = name;
        this.uid = uid;
    }
    
    public String getUID(){
        return uid;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((uid == null) ? 0 : uid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Project other = (Project) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (uid == null) {
            if (other.uid != null) {
                return false;
            }
        } else if (!uid.equals(other.uid)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Project [name=" + ObjectUtils.defaultIfNull(name,"<null>") + ", uid=" + ObjectUtils.defaultIfNull(uid,"<null>") + "]";
    }

    @Override
    public int compareTo(Project o) {
        if (o.getName() == null) {
            return 1;
        }
        if (this.getName() == null) {
            return -1;
        }
        return this.getName().compareTo(o.getName());
    }
    
}