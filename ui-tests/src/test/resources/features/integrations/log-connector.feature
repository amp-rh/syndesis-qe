# @sustainer: mmajerni@redhat.com
@stage-smoke
@ui
@log
@database
@integrations-log
Feature: Log Connector

  Background: Clean application state
    Given clean application state
    And reset content of "contact" table
    And insert into "contact" table
      | Joe | Jackson | Red Hat | db |
    And log into the Syndesis
    And navigate to the "Home" page
#
#
#  1. Check that log message exists

  @log-connector-error-message
  Scenario Outline: Check log message with body: <body> and context: <context>

    # create integration
    When click on the "Create Integration" link to create a new integration.
    Then check visibility of visual integration editor
    And check that position of connection to fill is "Start"

    When select the "PostgresDB" connection
    And select "Periodic SQL Invocation" integration action
    Then check "Next" button is "Disabled"

    When fill in periodic query input with "select * from contact limit 1" value
    And fill in period input with "1" value
    And select "Minutes" from sql dropdown
    And click on the "Next" button
    Then check visibility of page "Choose a Finish Connection"

    When select the "Log" connection
    And fill in values by element data-testid
      | contextloggingenabled | <context> |
      | bodyloggingenabled    | <body>    |

    Then click on the "Done" button

    When click on the "Save" link
    And set integration name "Integration_with_log_context_<context>_body_<body>"
    And publish integration
    Then Integration "Integration_with_log_context_<context>_body_<body>" is present in integrations list
    And wait until integration "Integration_with_log_context_<context>_body_<body>" gets into "Running" state

    And wait until integration Integration_with_log_context_<context>_body_<body> processed at least 1 message

    Then validate that logs of integration "Integration_with_log_context_<context>_body_<body>" contains string "<log_contains>"
    And validate that logs of integration "Integration_with_log_context_<context>_body_<body>" doesn't contain string "<log_not_contains>"

    Examples:
      | context | body  | log_contains                    | log_not_contains              |
      | true    | true  | Body: [[{"last_name":"Jackson", | "status":"done","failed":true |
      | false   | true  | Body: [[{"last_name":"Jackson", | Message Context:              |
      | true    | false | Message Context:                | Body: []                      |
      | false   | false |                                 | Message Context:              |

#
#  2. Check that log step works without any message or checkboxes
#
  @reproducer
  @gh-3786
  @log-connector-no-message
  Scenario: Check log without message

    When click on the "Create Integration" link to create a new integration.
    Then check visibility of visual integration editor
    And check that position of connection to fill is "Start"

    When select the "PostgresDB" connection
    And select "Periodic SQL Invocation" integration action
    Then check "Next" button is "Disabled"

    When fill in periodic query input with "select * from contact limit 1" value
    And fill in period input with "1" value
    And select "Minutes" from sql dropdown
    And click on the "Next" button
    Then check visibility of page "Choose a Finish Connection"

    And select the "Log" connection
    And fill in values by element data-testid
      | contextloggingenabled | false |
      | bodyloggingenabled    | false |
      | customtext            |       |

    Then click on the "Done" button

    # add logger without anything
    When add integration step on position "0"
    And select the "Log" connection
    And fill in values by element data-testid
      | contextloggingenabled | false |
      | bodyloggingenabled    | false |
      | customtext            |       |
    And click on the "Done" button

    When click on the "Save" link
    And set integration name "Integration_with_log2"
    And publish integration
    Then Integration "Integration_with_log2" is present in integrations list
    And wait until integration "Integration_with_log2" starting status gets into "Starting..." state
    And validate that logs of integration "Integration_with_log2" doesn't contain string "IllegalArgumentException"
    And wait until integration "Integration_with_log2" gets into "Running" state
