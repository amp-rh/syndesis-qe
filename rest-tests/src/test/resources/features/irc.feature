# @sustainer: avano@redhat.com

@rest
@irc
@delorean
Feature: Integration - IRC

  Background: Prepare
    Given clean application state
      And deploy IRC server
      And create IRC connection

  @integration-amq-irc
  @activemq
  @datamapper
  Scenario: AMQ to Send message
    Given deploy ActiveMQ broker
      And create ActiveMQ connection
      And create ActiveMQ "subscribe" action step with destination type "queue" and destination name "irc-input"
      And change "out" datashape of previous step to "JSON_INSTANCE" type with specification '{"header":"text", "messageContent":"text"}'
      And start mapper definition with name: "mapping 1"
      And MAP using Step 1 and field "/messageContent" to "/response/body"
      And create IRC "sendmsg" step with nickname "syndesis-publish" and channels "#spam1,#spam2"
      And change "in" datashape of previous step to "XML_INSTANCE" type with specification '<?xml version="1.0" encoding="UTF-8"?><response><body>message</body></response>'
    When create integration with name: "AMQ-IRC"
    Then wait for integration with name: "AMQ-IRC" to become active
    When connect IRC controller to channels "#spam1,#spam2"
      And publish message with content '{"header":"messageHeader", "messageContent":"Hi there!"}' to queue "irc-input"
    Then verify that the message with content '<?xml version="1.0" encoding="UTF-8" standalone="no"?><response><body>Hi there!</body></response>' was posted to channels "#spam1,#spam2"

  @integration-irc-ftp
  @ftp
  Scenario: Private message to FTP
    Given deploy FTP server
      And create FTP connection
      And create IRC "privmsg" step with nickname "listener" and channels "#listen"
      And create FTP "upload" action with values
        | fileName     | directoryName   | fileExist | tempPrefix    | tempFileName     |
        | message.txt  | upload          | Override  | copyingprefix | copying_test_out |
    When create integration with name: "IRC-FTP"
    Then wait for integration with name: "IRC-FTP" to become active
    When connect IRC controller to channels "#listen"
      And send message to IRC user "listener" with content 'Hello Listener!'
    Then verify that file "message.txt" was created in "upload" folder with content 'Hello Listener!' using FTP

  @ENTESB-9985
  @integration-irc-irc
  Scenario: Private message to Send message
    And create IRC "privmsg" step with nickname "listener" and channels "#listen"
    And create IRC "sendmsg" step with nickname "syndesis-publish" and channels "#spam1,#spam2"
    When create integration with name: "IRC-IRC"
    Then wait for integration with name: "IRC-IRC" to become active
    When connect IRC controller to channels "#spam1,#spam2"
    And send message to IRC user "listener" with content 'Hello!'
    Then verify that the message with content 'Hello!' was posted to channels "#spam1,#spam2"
