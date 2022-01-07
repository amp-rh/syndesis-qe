package io.syndesis.qe.steps.integrations.editor.add.connection;

import static org.assertj.core.api.Assertions.assertThat;

import static com.codeborne.selenide.Condition.visible;

import io.syndesis.qe.pages.integrations.editor.add.connection.ChooseAction;
import io.syndesis.qe.pages.integrations.editor.add.connection.actions.database.InvokeSql;
import io.syndesis.qe.pages.integrations.editor.add.connection.actions.database.PeriodicSql;
import io.syndesis.qe.pages.integrations.editor.add.connection.actions.fragments.ConfigureAction;
import io.syndesis.qe.utils.ByUtils;
import io.syndesis.qe.utils.TestUtils;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionActionSteps {

    private ChooseAction chooseAction = new ChooseAction();
    private ConfigureAction configureAction = new ConfigureAction();
    private PeriodicSql periodicSql = new PeriodicSql();
    private InvokeSql invokeSql = new InvokeSql();

    @When("^select \"([^\"]*)\" integration action$")
    public void selectIntegrationAction(String action) {
        if ("Create Opportunity".equals(action)) {
            log.warn("Action {} is not available", action);
            chooseAction.selectAction("Create Salesforce object");
        }
        chooseAction.selectAction(action);
    }

    @Then("^check visibility of connection action list$")
    public void verifyActionsList() {
        log.info("There must be action list loaded");
        chooseAction.getRootElement().shouldBe(visible);
    }

    @Then("^fill in \"(\\w+)\" action configure component input with \"([^\"]*)\" value$")
    public void fillActionConfigureField(String fieldDataTestid, String value) {
        log.info("Input should be visible");
        configureAction.fillInputByDataTestid(fieldDataTestid, value);
    }

    @Then("^fill in periodic query input with \"([^\"]*)\" value$")
    public void fillPerodicSQLquery(String query) {
        periodicSql.fillSqlInput(query);
    }

    @Then("^fill in period input with \"([^\"]*)\" value$")
    public void fillSQLperiod(String period) {
        periodicSql.fillSQLperiod(period);
    }

    @Then("^fill in invoke query input with \"([^\"]*)\" value$")
    public void fillInvokeSQLquery(String query) {
        invokeSql.fillSqlInput(query);
        //verifying of the querry sometimes fails but there is no success notifier, it just jumps to the next page
        TestUtils.sleepForJenkinsDelayIfHigher(3);
    }

    @Then("^check that sql query is \"([^\"]*)\"")
    public void checkQuery(String query) {
        TestUtils.sleepForJenkinsDelayIfHigher(3);
        assertThat(invokeSql.getSqlValue()).isEqualTo(query);
    }

    @When("^open Data Type parameters dialog$")
    public void openDataTypeDialog() {
        configureAction.getRootElement().find(ByUtils.dataTestId("describe-data-shape-form-parameters-button")).shouldBe(visible).click();
    }
}
