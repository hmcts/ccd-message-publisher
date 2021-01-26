# Topic Reader Utility
Utility for reading messages from a session-enabled subscription within an Azure Service Bus topic.

Reads one message at a time then exits.

## Running

Set up the following environment variables:
- `CONNECTION_STRING`: Azure Service Bus connection string
- `TOPIC`: Topic name
- `SUBSCRIPTION`: Session-enabled subscription name

Then to receive a single message:

`./gradlew run`
