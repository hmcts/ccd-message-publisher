@F-002 @Functional @Publishing
Feature: F-002: Publish a case event created through preview Data Store

Background:
    Given the publishing preview has isolated routing and a dedicated subscription
      And an appropriate test context as detailed in the test data source

@S-005
Scenario: publish the event for a newly created case with its routing properties and field data
    Given a user [with access to the publishing case type]
    When a request is prepared with appropriate values
      And it is submitted to call the [Create case] operation of [CCD Data Store]
    Then a positive response is received
      And the created case event is delivered to the dedicated preview subscription
