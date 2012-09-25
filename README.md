Overview
========
metrics-sampler is a java program which regularly queries metrics from a configured set of inputs, selects and renames them using regular expressions and sends them to a configured set of outputs. It supports JMX and JDBC as inputs and Graphite as output out of the box. Writing new extensions containing new inputs, outputs, samplers and selectors is pretty straight-forward.

Example Configuration
---------------------
Check out the following configuration as a quick-start:

	<!-- pool size is the number of threads to use for the samplers -->
	<configuration pool-size="10">
		<inputs>
			<!-- this is an example of a template - its values will be copied to any input that references it using the "template" attribute. Due to abstract=true it can never be used in a sampler 
			     and does not have to define all mandatory fields -->
			<jmx name="wls-template" abstract="true" username="admin" password="weblogic1" provider-packages="weblogic.management.remote" persistent-connection="true">
				<!-- we can choose to ignore certain object names using a list of regular expressions -->
				<ignore-object-names>
					<ignore-object-name regexp="^com\.oracle\.jrockit:type=Flight.+" />
				</ignore-object-names>
				<!-- a map of properties to pass to the JMX connector factory. You usually do not need this.
				<connection-properties>
					<entry key="jmx.remote.x.request.waiting.timeout" value="100" />
				</connection-properties>
				<!-- Using the socket-options you can configure some low level socket options for the RMI connections - 
				     most notably the SO_TIMEOUT (in ms) and the socket connection timeout (in ms) -->
				<socket-options connect-timeout="100" so-timeout="200" keep-alive="false" send-buffer-size="16384" receive-buffer-size="16384" />
				<!-- You can also define variables here -->
				<variables>
					<string name="whatever" value="value" /> 
				</variables>
			</jmx>
			
			<!-- WebLogic JMX server. Username, password, variables, ignores, etc. are taken from the template named "wls-template" -->
			<jmx name="wls01" url="service:jmx:t3://weblogic1.metrics-sampler.org:6001/jndi/weblogic.management.mbeanservers.runtime" template="wls-template" />
			<jmx name="wls02" url="service:jmx:t3://weblogic2.metrics-sampler.org:6001/jndi/weblogic.management.mbeanservers.runtime" template="wls-template" />
			
			<!-- Tomcat JMX server -->
			<jmx name="tomcat01" url="service:jmx:rmi:///jndi/rmi://tomcat.metrics-sampler.org:7001/jmxrmi" persistent-connection="true" />

			<!-- Execute the given query(ies) over JDBC and use the first column as metric name, the second as metric value and the third one as timestamp. You will need to have the JDBC driver in the lib/ directory -->
			<jdbc name="oracle01" url="jdbc:oracle:thin:@//oracle1.metrics-sampler.org:1521/EXAMPLE" username="user" password="password" driver="oracle.jdbc.OracleDriver">
				<query>select replace(T2.host_name||'.'||T2.instance_name||'.'||replace(replace(replace(replace(metric_name,'/',''),'%','Perc'),'(',''),')',''),' ','_') as metric, value, (25200 + round((end_time - to_date('01-JAN-1970','DD-MON-YYYY')) * (86400),0))*1000 as dt from gv$sysmetric T1, gv$instance T2 where T1.intsize_csec between 1400 and 1600 and T1.inst_id = T2.INST_ID</query>
			</jdbc>
			
			<!-- Apache mod_qos status page -->
			<mod-qos name="apache01" url="http://apache1.metrics-sampler.org:80/qos-viewer?auto" username="user" password="pass" auth="basic"/>
		</inputs>
		<outputs>
			<!-- Write to the standard output -->
			<console name="console" />
			<!-- Send to graphite running on port 2003 -->
			<graphite name="graphite" host="graphite.metrics-sampler.org" port="2003" />
		</outputs>
		
		<!-- we can also define some global variables that will be available in all samplers (unless overridden) -->
		<variables>
			<string name="tomcat.port" value="8080" />
		</variables>
		
		<!-- we define some regular expressions in groups so that we can reuse them later in the samplers -->
		<selector-groups>
			<selector-group name="wls">
				<!-- from-name is a regular expression that is matched against e.g. the JMX Metric Name (consisting of canonical object name # attribute name). The string can also contain references to variables in the form ${name}. 
				     to-name is an expression (not a regular expression) that can use variables for things like captured groups from the name's regular expression. -->
				<regexp from-name="com\.bea:Name=DataSource_(.+),ServerRuntime=.+,Type=JDBCOracleDataSourceRuntime\.(ActiveConnectionsAverageCount|ActiveConnectionsCurrentCount|ActiveConnectionsHighCount|ConnectionDelayTime|ConnectionsTotalCount|CurrCapacity|CurrCapacityHighCount|FailuresToReconnectCount|HighestNumAvailable|HighestNumUnavailable|LeakedConnectionCount|NumAvailable|NumUnavailable|ReserveRequestCountWaitSecondsHighCount|WaitingForConnection.*)" to-name="${prefix}.jdbc.${name[1]}.${name[2]}" />
				<regexp from-name="com\.bea:Name=JTARuntime,ServerRuntime=.*,Type=JTARuntime\.(.*TotalCount)" to-name="${prefix}.jta.${name[1]}" />
				<regexp from-name="com\.bea:Name=ThreadPoolRuntime,ServerRuntime=.*,Type=ThreadPoolRuntime\.(CompletedRequestCount|ExecuteThreadIdleCount|ExecuteThreadTotalCount|HoggingThreadCount|MinThreadsConstraintsCompleted|MinThreadsConstraintsPending|PendingUserRequestCount|QueueLength|SharedCapacityForWorkManagers|StandbyThreadCount|Throughput)" to-name="${prefix}.threads.${name[1]}"/>
				<regexp from-name="com\.bea:Name=.*,ServerRuntime=.*,Type=JRockitRuntime\.(JvmProcessorLoad|TotalGarbageCollectionCount|TotalGarbageCollectionTime|FreePhysicalMemory|UsedPhysicalMemory|Uptime)" to-name="${prefix}.jrockit.${name[1]}" />
			</selector-group>
			<selector-group name="tomcat">
				<!-- note that you can use variables in the from-name too. These must be explicitly defined in the sampler (or come from the input reader) -->
				<regexp from-name="Catalina:type=GlobalRequestProcessor,name=http-${tomcat.port}.\.(requestCount|bytesSent|bytesReceived)" to-name="${prefix}.http.${name[1]}"/>
			</selector-group>
			<selector-group name="mod_qos">
				<regexp from-name=".*,metric=([^,]+),path=/([^.]+)\.(current|limit)" to-name="${prefix}.${name[2]}.${name[1]}.${name[3]}"/>
				<regexp from-name=".*,metric=([^,]+)$" to-name="${prefix}.${name[1]}"/>
				<regexp from-name=".*,metric=([^,]+)\.(current|limit)" to-name="${prefix}.${name[1]}.${name[2]}"/>
			</selector-group>
		</selector-groups>
		
		<!-- These are the actual active runtime components that sample the date from their input, use the given selectors to determine which metrics are relevant (and rename them) and sends them to the given outputs.
		     An input without a sampler does not do anything. The samplers are scheduled at a constant rate (with the given interval) to a thread pool of the size defined above. -->
		<samplers>
			<!-- template defining common values for weblogic samplers. If you define any of the attributes / child elements in the samplers that use this template, these values here will be lost (not appended to). -->
			<sampler name="wls" abstract="true" outputs="graphite" interval="10">
				<selectors>
					<use-group name="wls" />
				</selectors>
				<!-- these variables are added to the one's defined by the input itself (e.g. input.name, input.host, input.fqhn, input.ip, input.hostname, etc.)
				<variables>
					<string name="prefix" value="backend.${input.name}" />
				</variables>
			</sampler>
			
			<!-- fetch data from wls01 input, use the regular expressions in a group named "wls" to select and rename metrics and send them to graphite every 10 seconds. If you specify a child its value will replace
			     the values from the template - e.g. lists of selectors will not be merged -->
			<sampler input="wls01" template="wls" />
			<sampler input="wls02" template="wls" />
			
			<sampler input="tomcat01" outputs="graphite" interval="10">
				<variables>
					<string name="prefix" value="frontend.${input.name}" />
					<string name="port" value="8080" />
				</variables>
				<selectors>
					<use-group name="tomcat" />
				</selectors>
			</sampler>

			<!-- setting quiet to true causes the sampler to log connection problems using debug level - thus preventing the problem to be logged in the standard configuration. This is
			     useful if the input is a source that is not always available but you want to still get metrics when it is available. -->
			<sampler input="apache01" outputs="graphite" interval="10" quiet="true">
				<variables>
					<string name="prefix" value="frontend.${input.name}" />
				</variables>
				<selectors>
					<use-group name="mod_qos" />
				</selectors>
			</sampler>

			<!-- you can use disabled="true" to disable a sampler without removing / commenting it out. note that it still needs to be valid. -->
			<sampler input="oracle01" outputs="graphite" interval="10" disabled="true">
				<variables>
					<string name="prefix" value="database.${input.name}" />
				</variables>
				<selectors>
					<!-- we can of course specify regular expressions directly here too. -->
					<regexp from-name="(.*)" to-name="${name[1]}"/>
				</selectors>
			</sampler>
		</samplers>
	</configuration>

Supported Inputs
-----------------
* Java Management Extensions (JMX) - queries object names and attributes from a remote JMX server. The reader caches all meta-data until a reconnect. The name of the metrics consist of the canonicalized object name + '#' + attribute name.
* JDBC - sequentially execute a list of SQL queries and interpret the returned rows as metrics. The reader currently does not reuse the data-base connection between samplings. Queries must return either two or three columns - the first one is the metric's name and the second one is its value. The optional third one is a timestamp (in milliseconds since epoch start).
* mod_qos - parses the output of the mod_qos status page (with option ?auto) and exposes the values in a more usable format. The reader uses non-persistent HTTP connection and queries both metadata and data when opened.

Supported Selectors
-------------------
* Regular expressions selector
Matches metrics by their names using regular expressions. Each metric can then be renamed using expressions which can refer to the input's name and the matching groups of the regular expressions.

Supported Outputs
-----------------
* Console (STDOUT)
* Graphite [http://graphite.wikidot.com]

Variables
---------
Variables can be defined in the global context, in the inputs and in the samplers. Additionally there are some variables that are automatically generated by the inputs like input.name. If a variable with the same name is defined in multiple contexts, its value will be taken from the definition in the most specific context - global variables will be overridden by variables defined in the inputs and in the samplers. Variables defined in an input will be overridden by variables defined in the samplers.  

Quick start
===========
1. Download the metrics-sampler-distribution-<version>-all.tar.gz
2. Unpack it into a directory of your choice, e.g. metrics-sampler-<version>
3. Create a configuration in config/config.xml using config/config.xml.example as starting point
4. If you want to list all the metrics from your configured inputs you can call "bin/metrics-sampler.sh metadata". This will output all names and descriptions of the available metrics for each input.
5. Run "bin/metrics-sampler.sh check" to verify that each selector of each sampler matches at least one metric
6. Start the daemon using "bin/metrics-sampler.sh start". Logs are located in logs/metrics-sampler.log and in logs/console.out
7. You can stop the daemon using "bin/metrics-sampler.sh stop"

Extensions
==========
It should be pretty easy to extend the program with new inputs, outputs, samplers and selectors. For this you will need to create a new module/project like this (you could also check out the extensions-* modules which use the same mechanism):
* Add metrics-sampler-core to the classpath of your program/module (e.g. maven dependency)
* Create the file "META-INF/services/org.metricssampler.service.Extension" in src/main/resources (or in any location that lands in your compiled jar) containg the fully qualified class name of a class that implements org.metricssampler.service.Extension
* Your org.metricssampler.service.Extension implementation will return your custom XBeans (XML configuration beans)
* You will have to implement an org.metricssampler.service.LocalObjectFactory (e.g. by extending org.metricssampler.service.AbstractLocalObjectFactory) so that you can create the actual input readers, output writers etc. from their configurations
* Put the resulting jar file on your classpath and you are ready to go (e.g. copy it to the lib/ directory of your installation)
* If you think the extension might be of any use to anyone else - please share it.

Internals
=========
* I chose to use slf4j in all classes with logback under the hood as it is pretty simple to configure
* The graphite writer currently disconnects on each sampling but could be improved to keep the connection (or even better let that be configurable)
* I use XStream to load the XML configuration. The XML is mapped to *XBean instances which are basically pojos with the some added abilities like validating their data and converting themselves to the more usable and configuration format independent *Config pojos. The *Config pojos are value objects.
* The core implementation took about 2 days. In that light it might be more understandable why there are no unit tests. I intend however to write some in the future.
* Currently the stop consists of killing the process. It would be nice to implement a graceful shutdown which can stop all samplers and disconnect all readers and writers
* mod_qos uses an URLConnection to fetch the data. Currently we also have a trivial implementation of basic authentication. It would be nice to switch to httpcomponents so that we get all the nice stuff out-of-the-box (also a better API) - maybe even persistent connections.

Compatibility
=============
* Tested with Hotspot/JRockit JVM 1.6
* Tested with Tomcat 7 and Weblogic Server 12c (provided that wlfullclient.jar (the jmx client and t3 protocol jars) is on the classpath)
* You might need to add -Dsun.lang.ClassLoader.allowArraySyntax=true as JVM parameter in the metrics-sampler.sh script if you are connecting using JVM 1.6 client to a JVM 1.5 server
