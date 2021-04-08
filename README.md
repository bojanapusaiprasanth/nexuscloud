
# Cloud Elements Nexus Java Client

This repository hosts the Nexus java client to communicate to Nexus platform.

## Build & Code coverage
Can generate and view code coverage via the following command 
```console
$  mvn clean jacoco:prepare-agent install jacoco:report && open -a "Google Chrome" target/site/jacoco/index.html
```
Build jar file
```console
$ mvn clean install
```
## Usage

### Java
The Java distribution is packaged in a fat JAR and pushed to Github Packages. It can be used as a dependency by adding 
Github Packages as a Maven repository and including the package in your `pom.xml`.

See [Github Packages documentation](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-apache-maven-for-use-with-github-packages)
for details.

Example (pom.xml):
```xml
<project>
    <repositories>
        <repository>
            <id>github</id>
            <name>Github cloud-elements Maven Packages</name>
            <url>https://maven.pkg.github.com/cloud-elements</url>
        </repository>
    </repositories>
    <dependencies>
        <dependency>
            <groupId>com.cloudelements.nexus</groupId>
            <artifactId>nexus-java-client</artifactId>
            <version>${RELEASE_VERSION}</version>
        </dependency>
    </dependencies>
</project>
```
### Publish Consumer Service API
1. Publish the consumer service to ready accept the message from Nexus platform. Refer below sample for more information.
``` java
@RestController
@RequestMapping(path = "/nexus/event")
public class NexusIncomingEventConsumerApi extends AbstractNexusConsumerApi<SobaMap> {

    @Api(requiresUserOrgAuth = false)
    @RequestMapping(value = {"/dispatch/{queueName}"}, method = RequestMethod.POST,
            consumes = {APPLICATION_JSON_VALUE, APPLICATION_X_PROTOBUF})
    public ResponseEntity<DispatcherResponse> onMessage(@PathVariable String queueName,
                                                        @RequestHeader HttpHeaders headers,
                                                        @RequestBody SobaMap incomingEvent) {
        return super.onMessage(queueName, headers, incomingEvent);
    }

    @Override
    protected void handle(String queueName, Map<String,Object> message) throw Exception {
        //Do work with message and handle it properly
        //throw Exception if something wrong during process.
    }
}
```
2. Register or map this endpoint url to specific queueName in Nexus platform to subscribe message for given queue name
``` console
http://localhost:9090/event/dispatch/{queueName}
```