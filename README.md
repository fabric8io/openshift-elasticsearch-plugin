# OpenShift ElasticSearch plugin
This is an OpenShift plugin to ElasticSearch to:

* Dynamically update the SearchGuard ACL based on a user's name
* Transform kibana index requests to support multitenant deployments
* Seed the Searchguard index `config`, `roles`, `rolesmapping`, and `actiongroups` types

## Configuring your initial ACLs
With the update to use Searchguard-2 and Searchguard-SSL for ES 2.3.x, the
OpenShift-Elasticsearch-Plugin now populates the initial ACL and configuration in
a manner similar to how the sgadmin tool from [Searchguard](https://github.com/floragunncom/search-guard#dynamic-configuration)
describes.  Configurations are read in from `searchguard.config.path` (Default: `/opt/app-root/src/sgconfig/`).
The files should match the name patterns ["sg_config.yml", "sg_roles.yml",
"sg_rolesmapping.yml", "sg_actiongroups.yml"].  The plugin does not use internal_users
as Searchguard does. Roles are used to define actions that are allowed for
particular indices and types. RolesMapping map user names to specific roles.

You can view sample configurations [here] (./samples/).

As with `sgadmin`, the plugin needs to use the certificate with a DN that matches
the `searchguard.authcz.admin_dn` as defined in the ES config to be able to
update the Searchguard index. You can specify the certificate and truststore information
for the esClient with the following properties.

|Property|Description|
|-------|--------|
|*_openshift.searchguard.keystore.path_*|The certificate that contains the cert and key for the admin_dn. Default: *_/usr/share/elasticsearch/config/admin.jks_*|
|*_openshift.searchguard.truststore.path_*|The truststore that contains the certificate for Elasticsearch. Default: *_/usr/share/elasticsearch/config/logging-es.truststore.jks_*|
|*_openshift.searchguard.keystore.password_*|The password to open the keystore. Default: *_kspass_*|
|*_openshift.searchguard.truststore.password_*|The password to open the truststore. Default: *_tspass_*|
|*_openshift.searchguard.keystore.type_*|The file type for the keystore. JKS or PKCS12 are accepted. Default: *_JKS_*|
|*_openshift.searchguard.truststore.type_*|The file type for the truststore. JKS or PKCS12 are accepted. Default: *_JKS_*|

## Configure the projects for '.operations'
You can now configure which projects are deemed part of the .operations index for ACL
configuration.

In your config file:
```
openshift.operations.project.names: ["default", "openshift", "openshift-infra"]
```

The current default is "default", "openshift", "openshift-infra", "kube-system".
The names must all be in lower-case to be properly matched.


## Development
Following are the dependencies

* [ElasticSearch 2.3.3] (https://github.com/elastic/elasticsearch/tree/2.3)
* [Search-Guard 2.3.3.3] (https://github.com/floragunncom/search-guard/tree/2.3.3.3)
* [Search-Guard-SSL 2.3.3.13] (https://github.com/floragunncom/search-guard-ssl/tree/2.3.3)

### Debugging and running from Eclipse

* Install ES

* Create a run configuration
 * Main Class
 ![Main class](images/eclipse_run_main.png)

 * VM args:

 ````-Des.path.home=${env_var:ES_HOME} -Delasticsearch -Des.foreground=yes -Dfile.encoding=UTF-8 -Delasticsearch -Xms256m -Xmx1g -Djava.awt.headless=true -XX:+UseParNewGC -XX:+UseConcMarkSweepGC -XX:CMSInitiatingOccupancyFraction=75 -XX:+UseCMSInitiatingOccupancyOnly -XX:+HeapDumpOnOutOfMemoryError -XX:+DisableExplicitGC````

![VM Args](images/eclipse_run_args.png)

 * Environment Variables:

![Environment Variables](images/eclipse_run_env.png)   
