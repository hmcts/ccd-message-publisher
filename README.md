# CCD Message Publisher

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=uk.gov.hmcts.reform%3Accd-message-publisher&metric=alert_status)](https://sonarcloud.io/dashboard?id=uk.gov.hmcts.reform%3Accd-message-publisher)

Provides the capability to publish CCD message queue candidates to [Azure Service Bus](https://azure.microsoft.com/en-gb/services/service-bus/) on a schedule.

## Getting Started

Please note that this microservice is also available within [ccd-docker](https://github.com/hmcts/ccd-docker).

### Prerequisites

- [JDK 11](https://java.com)
- [Docker](https://www.docker.com)

### Building and deploying the application

#### Building the application

The project uses [Gradle](https://gradle.org) as a build tool. It already contains a
`./gradlew` wrapper script, so there's no need to install Gradle.

To build the project execute the following command:

```bash
  ./gradlew build
```

#### Running the application

Create the image of the application by executing the following command:

```bash
  ./gradlew assemble
```

Create docker image:

```bash
  docker-compose build
```

Run the distribution (created in the `build/libs` directory)
by executing the following command:

```bash
  docker-compose up
```

This will start the API container exposing the application's port (`4456`).

In order to test if the application is up, you can call its health endpoint:

```bash
  curl http://localhost:4456/health
```

You should get a response similar to this:

```
  {"status":"UP","components":{"db":{"status":"UP","details":{"database":"PostgreSQL","validationQuery":"isValid()"}},"diskSpace":{"status":"UP","details":{"total":47111729152,"free":3854127104,"threshold":10485760,"exists":true}},"jms":{"status":"UP","details":{"provider":"ActiveMQ"}},"ping":{"status":"UP"},"refreshScope":{"status":"UP"}}}
```

#### Alternative script to run application

To skip all the setting up and building, just execute the following command:

```bash
  ./bin/run-in-docker.sh -c -i
```

For more information:

```bash
  ./bin/run-in-docker.sh -h
```

## Azure Service Bus & Local Testing

There is currently no Azure Service Bus emulator available for local/offline testing, and therefore
the CCD Message Publisher provides an alternative option through an embedded [ActiveMQ](http://activemq.apache.org/)
instance. This is possible due to both Azure Service Bus and ActiveMQ support for the AMQP 1.0 protocol.

### ActiveMQ

**By default** the CCD Message Publisher is configured to start in "dev" mode with ActiveMQ enabled when run locally.

ActiveMQ information, including details on published messages, can be accessed via the 
Hawtio Management Console UI at http://localhost:4456/hawtio.

Note that the ActiveMQ menu option on Hawtio will only show up once the first message has been published, and
data will not persist across restarts. This is meant for dev/test purposes only and ActiveMQ and the Hawtio console
are disabled in production.

### Azure Service Bus

To enable publishing to an Azure Service Bus destination:

1. Comment the `SPRING_PROFILES_ACTIVE` environment variable in the `docker-compose.yml`
1. Set the Azure Service Bus connection string in the `SERVICE_BUS_CONNECTION_STRING` environment variable
1. Set the destination in the `CCD_CASE_EVENTS_DESTINATION` environment variable - this can be either a topic or a queue
1. Restart the application

## Developing

### Unit tests

To run all unit tests execute the following command:
```bash
  ./gradlew test
```

### Integration tests

To run all integration tests execute the following command:
```bash
  ./gradlew integration
```

### Code quality checks
We use [Checkstyle](http://checkstyle.sourceforge.net/). 
To run all local checks execute the following command:

```bash
  ./gradlew check
```

Additionally, [SonarQube](https://sonarcloud.io/dashboard?id=uk.gov.hmcts.reform%3Accd-message-publisher) 
analyses are performed on all remote code.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

