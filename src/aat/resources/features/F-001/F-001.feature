@F-001
Feature: F-001: Application identity and documentation

Background:
    Given an appropriate test context as detailed in the test data source

@S-003 @Smoke
Scenario: must identify the deployed Message Publisher
    When a request is prepared with appropriate values
      And it is submitted to call the [Welcome] operation of [CCD Message Publisher]
    Then a positive response is received
      And the response [has the 200 OK code]
      And the response has all other details as expected.

@S-004 @Smoke
Scenario: must serve the Message Publisher OpenAPI document
    When a request is prepared with appropriate values
      And it is submitted to call the [OpenAPI] operation of [CCD Message Publisher]
    Then a positive response is received
      And the response [has the 200 OK code]
      And the response has all other details as expected.
