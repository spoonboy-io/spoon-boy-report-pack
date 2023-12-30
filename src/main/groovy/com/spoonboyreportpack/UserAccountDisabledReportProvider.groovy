package com.spoonboyreportpack

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.response.ServiceResponse
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import java.sql.Connection
import io.reactivex.rxjava3.core.Observable

class UserAccountDisabledReportProvider extends AbstractReportProvider{
	protected MorpheusContext morpheusContext
	protected Plugin plugin

	UserAccountDisabledReportProvider(Plugin plugin, MorpheusContext morpheusContext) {
		this.morpheusContext = morpheusContext
		this.plugin = plugin
	}
	/**
	 * Returns the Morpheus Context for interacting with data stored in the Main Morpheus Application
	 *
	 * @return an implementation of the MorpheusContext for running Future based rxJava queries
	 */
	@Override
	MorpheusContext getMorpheus() {
		return this.morpheusContext
	}

	/**
	 * Returns the instance of the Plugin class that this provider is loaded from
	 * @return Plugin class contains references to other providers
	 */
	@Override
	Plugin getPlugin() {
		return this.plugin
	}

	/**
	 * A unique shortcode used for referencing the provided provider. Make sure this is going to be unique as any data
	 * that is seeded or generated related to this provider will reference it by this code.
	 * @return short code string that should be unique across all other plugin implementations.
	 */
	@Override
	String getCode() {
		return "spoon-boy-user-account-disabled-report"
	}

	/**
	 * Provides the provider name for reference when adding to the Morpheus Orchestrator
	 * NOTE: This may be useful to set as an i18n key for UI reference and localization support.
	 *
	 * @return either an English name of a Provider or an i18n based key that can be scanned for in a properties file.
	 */
	@Override
	String getName() {
		return "Disabled User accounts"
	}

	@Override
	ServiceResponse validateOptions(Map opts) {
		return ServiceResponse.success()
	}

	@Override
	void process(ReportResult reportResult) {

		/*
		 select username, email, DATE_FORMAT(u.date_created,'%D %M %Y') created, DATE_FORMAT(u.last_login_date, '%D %M %Y') lastLogin, if(enabled, 'NO', 'YES') disabled from user u inner join account a where a.name = 'Neo' and u.enabled = 1 and a.id = u.account_id order by u.date_created desc;
		*/

		morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
        Long displayOrder = 0
        List<GroovyRowResult> repResults = []

        Integer disabledUsers = 0

        Connection dbConnection

        try {
            dbConnection = morpheus.report.getReadOnlyDatabaseConnection().blockingGet()
            def accountName = reportResult.getAccount().getName()

            repResults = new Sql(dbConnection).rows("select username, email, DATE_FORMAT(u.date_created,'%D %M %Y') created, DATE_FORMAT(u.last_login_date, '%D %M %Y') lastLogin, if(enabled, 'NO', 'YES') disabled from user u inner join account a where a.name = '" + accountName + "' and u.enabled = 0 and a.id = u.account_id order by u.date_created desc;")

        } finally {
            morpheus.report.releaseDatabaseConnection(dbConnection)
        }

        Observable<GroovyRowResult> observable = Observable.fromIterable(repResults) as Observable<GroovyRowResult>
                observable.map{ resultRow ->

                    def Map<String,Object> data = [:]

                    data = [
                            username: resultRow.username,
                            email: resultRow.email,
                            created : resultRow.created,
                            lastLogin: resultRow.lastLogin,
                            disabled : resultRow.disabled
                    ]

                    // create summary metrics
                    disabledUsers ++

                    ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
                    return resultRowRecord

                }.buffer(50).doOnComplete {
                    morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
                }.doOnError { Throwable t ->
                    morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
                }.subscribe {resultRows ->
                    morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
                }

        // prep header
        Map<String,Object> headerData = [
            disabledUsers: disabledUsers
        ]

        ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_HEADER, displayOrder: displayOrder++, dataMap: headerData)
        morpheus.report.appendResultRows(reportResult,[resultRowRecord]).blockingGet()

	}

	/**
	 * A short description of the report for the user to better understand its purpose.
	 * @return the description string
	 */
	@Override
	String getDescription() {
		return "Provides a list of users who have a disabled account"
	}

	/**
	 * Gets the category string for the report. Reports can be organized by category when viewing.
	 * @return the category string (i.e. inventory)
	 */
	@Override
	String getCategory() {
		return "user"
	}

	/**
	 * Only the owner of the report result can view the results.
	 * @return whether this report type can be read by the owning user only or not
	 */
	@Override
	Boolean getOwnerOnly() {
		return false
	}

	/**
	 * Some reports can only be run by the master tenant for security reasons. This informs Morpheus that the report type
	 * is a master tenant only report.
	 * @return whether or not this report is for the master tenant only.
	 */
	@Override
	Boolean getMasterOnly() {
		return false
	}

	/**
	 * Detects whether or not this report is scopable to all cloud types or not
	 * TODO: Implement this for custom reports (NOT YET USABLE)
	 * @return whether or not the report is supported by all cloud types. This allows for cloud type specific reports
	 */
	@Override
	Boolean getSupportsAllZoneTypes() {
		return true
	}

	@Override
	List<OptionType> getOptionTypes() {
		return null
	}

	/**
	 * Presents the HTML Rendered output of a report. This can use different {@link Renderer} implementations.
	 * The preferred is to use server side handlebars rendering with {@link com.morpheusdata.views.HandlebarsRenderer}
	 * <p><strong>Example Render:</strong></p>
	 * <pre>{@code
	 *    ViewModel model = new ViewModel()
	 * 	  model.object = reportRowsBySection
	 * 	  getRenderer().renderTemplate("hbs/instanceReport", model)
	 *}</pre>
	 * @param reportResult the results of a report
	 * @param reportRowsBySection the individual row results by section (i.e. header, vs. data)
	 * @return result of rendering an template
	 */
	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<Map<String, List<ReportResultRow>>> model = new ViewModel<>()
		model.object = reportRowsBySection
		getRenderer().renderTemplate("hbs/userAccountDisabledReport", model)
	}
}
