# OpenShift SearchGuard Sync
This is an OpenShift plugin to ElasticSearch to:

* Dynamically update the SearchGuard ACL based on a user's name

## Development
Following are the dependencies

* ElasticSearch 1.5.2

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