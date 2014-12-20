// PipelineActivityComponent.java --
//
// PipelineActivityComponent.java is part of ElectricCommander.
//
// Copyright (c) 2005-2013 Electric Cloud, Inc.
// All rights reserved.

package ecplugins.PipelineActivity.client;

import static ecplugins.PipelineActivity.client.PipelineActivityResources.RESOURCES;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ca.nanometrics.gflot.client.DataPoint;
import ca.nanometrics.gflot.client.PlotModel;
import ca.nanometrics.gflot.client.SeriesHandler;
import ca.nanometrics.gflot.client.SimplePlot;
import ca.nanometrics.gflot.client.options.PlotOptions;

import com.electriccloud.commander.client.ChainedCallback;
import com.electriccloud.commander.client.domain.ErrorCode;
import com.electriccloud.commander.client.domain.Job;
import com.electriccloud.commander.client.domain.JobOutcome;
import com.electriccloud.commander.client.domain.LogEntry;
import com.electriccloud.commander.client.domain.ObjectType;
import com.electriccloud.commander.client.domain.Order;
import com.electriccloud.commander.client.domain.Project;
import com.electriccloud.commander.client.domain.Property;
import com.electriccloud.commander.client.domain.PropertySheet;
import com.electriccloud.commander.client.domain.State;
import com.electriccloud.commander.client.domain.StateDefinition;
import com.electriccloud.commander.client.domain.Transition;
import com.electriccloud.commander.client.domain.Workflow;
import com.electriccloud.commander.client.domain.WorkflowDefinition;
import com.electriccloud.commander.client.requests.CommanderRequest;
import com.electriccloud.commander.client.requests.FindObjectsFilter;
import com.electriccloud.commander.client.requests.FindObjectsRequest;
import com.electriccloud.commander.client.requests.GetJobInfoRequest;
import com.electriccloud.commander.client.requests.GetPropertiesRequest;
import com.electriccloud.commander.client.requests.GetPropertyRequest;
import com.electriccloud.commander.client.requests.GetWorkflowDefinitionsRequest;
import com.electriccloud.commander.client.requests.SetPropertyRequest;
import com.electriccloud.commander.client.responses.CommanderError;
import com.electriccloud.commander.client.responses.DefaultPropertyCallback;
import com.electriccloud.commander.client.responses.FindObjectsResponse;
import com.electriccloud.commander.client.responses.FindObjectsResponseCallback;
import com.electriccloud.commander.client.responses.JobCallback;
import com.electriccloud.commander.client.responses.PropertyCallback;
import com.electriccloud.commander.client.responses.PropertySheetCallback;
import com.electriccloud.commander.client.responses.WorkflowDefinitionListCallback;
import com.electriccloud.commander.gwt.client.ComponentBase;
import com.electriccloud.commander.gwt.client.ui.ErrorPanel;
import com.electriccloud.commander.gwt.client.ui.TitledPanel;
import com.electriccloud.commander.gwt.client.util.CommanderUrlBuilder;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.regexp.shared.MatchResult;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

public class PipelineActivityComponent extends ComponentBase {

	// The name of the workflow definition whose corresponding workflows will
	// be displayed. Expected to exist in GET.
	private String m_workflowDefinitionName = Window.Location
			.getParameter("workflowDefinitionName");

	// The name of the project to which the workflow definition belongs.
	// Expected to exist in GET.
	private String m_projectName = Window.Location.getParameter("projectName");

	// The ordered list of states to show in the activity table. Determined
	// based on which state definitions have the SHOW property set.
	private List<String> m_statesToShow;

	// the filter panel comboxes for selecting Project and WorkflowDefinition
	final private ListBox m_projectBox = new ListBox(false);
	final private ListBox m_workflowDefinitionBox = new ListBox(false);

	// the button to allow user set current selection project and workflow
	// definition as the default
	final private ChangeDetectButton m_setDefault = new ChangeDetectButton(
			"Set as Default");
	// the property path to save the user selections as default value for
	// project and workflow definition.
	private String defaultProjectPropertyPath = "/myUser/pipelineactivity/defaultproject";
	private String defaultWorkflowDefinitionPropertyPath = "/myUser/pipelineactivity/defaultworkflowdefinition";

	// The overall panel including the header and the table of workflows.
	private TitledPanel m_activityPanel;

	// The table of recent workflows, i.e. instances of the given workflow
	// definition.
	private FlexTable m_activity;

	// The CSS formatter for the activity table.
	private FlexCellFormatter m_formatter;

	// A panel at the bottom of the page where error messages can be printed.
	private ErrorPanel m_errorPanel;

	// This map is populated with all workflow filters provided as GET
	// parameters in the URL.
	// - Key: filter property
	// - Value: filter value
	private Map<String, String> m_workflowFilters = new HashMap<String, String>();

	// For non-completed workflows, this map is populated with a list of
	// manual transitions for the active state. These transitions represent
	// actions that the user can currently take on that workflow.
	// - Key: Workflow name
	// - Value: Outbound manual transitions for active state
	private Map<String, List<Transition>> m_manualTransitionMap = new HashMap<String, List<Transition>>();

	// This map is populated with all states.
	// - Key: Workflow name + state name
	// - Value: State object
	private Map<String, State> m_stateMap = new HashMap<String, State>();

	// This map is populated with subjobs for states that show up in the
	// activity table.
	// - Key: Workflow name + state name
	// - Value: Job object
	private Map<String, Job> m_subJobMap = new HashMap<String, Job>();

	// The property from which to retrieve the value of the first column;
	// defaults to workflowName if not
	private String m_keyProperty;

	// The workflows to be displayed in the activity table, as returned by the
	// server.
	private FindObjectsResponse m_workflows;

	// The panel on the right side containing charts (if defined on the
	// workflow definition).
	private VerticalPanel m_chartPanel;

	// The individual charts are stored while they're processed.
	private List<Widget> m_charts = new ArrayList<Widget>();

	// The map of properties that are being charted to the series that chart
	// them.
	private Map<String, SeriesHandler> m_chartProperties = new HashMap<String, SeriesHandler>();

	// The name of the property on a state definition which determines whether
	// the corresponding states show up in the activity table.
	private static final String SHOW = "pipeline_activity_show";

	// The name of the property sheet on a workflow definition which defines
	// chart properties and colors.
	private static final String CHARTS = "pipeline_activity/charts";

	// The default chart dimensions and colors.
	private static final int CHART_WIDTH = 325;
	private static final int CHART_HEIGHT = 140;
	private static final String CHART_DEFAULT_COLOR = "#000000";

	// ~ Functions
	// --------------------------------------------------------------

	/**
	 * This function is called by SDK infrastructure to initialize the UI parts
	 * of this component.
	 *
	 * @return A widget that the infrastructure should place in the UI; usually
	 *         a panel.
	 */
	@Override
	public Widget doInit() {
		PipelineActivityStyles css = RESOURCES.css();

		// Inject the contents of the CSS file.
		css.ensureInjected();

		// Process the GET parameters in the URL.
		processUrlParameters();

		// Create the panels.
		VerticalPanel vertical = new VerticalPanel();
		HorizontalPanel horizontal = new HorizontalPanel();
		HorizontalPanel filterPanel = new HorizontalPanel();
		Label projectLable = new Label("Project:");
		Label workflowDefinitionLabel = new Label("Workflow Definition:");
		filterPanel.setSpacing(20);
		filterPanel.add(projectLable);
		filterPanel.add(m_projectBox);
		m_projectBox.addChangeHandler(new ChangeHandler() {

			@Override
			public void onChange(ChangeEvent event) {
				String projectName = m_projectBox.getValue(m_projectBox
						.getSelectedIndex());
				m_projectName = projectName;
				updateSetDefaultButtonStatus();
				updateWorkflowDefinition(projectName, "");
			}

		});

		m_workflowDefinitionBox.addChangeHandler(new ChangeHandler() {

			@Override
			public void onChange(ChangeEvent event) {
				String workflowDefName = m_workflowDefinitionBox
						.getValue(m_workflowDefinitionBox.getSelectedIndex());
				m_workflowDefinitionName = workflowDefName;
				updateSetDefaultButtonStatus();

				// clear the world
				m_manualTransitionMap.clear();
				m_stateMap.clear();
				m_subJobMap.clear();
				m_charts.clear();
				m_chartProperties.clear();
				m_chartPanel.clear();
				m_activity.removeAllRows();

				// reload whole workflow pipeline.
				/**
				 * Make all workflow definition related requests, and use the
				 * responses to search for workflows.
				 */
				getRequestManager().doRequest(new ChainedCallback() {
					@Override
					public void onComplete() {
						getWorkflows();
					}
				}, getStateDefinitionsRequest(), getChartDefinitionsRequest());
			}
		});

		m_setDefault.addClickHandler(new ClickHandler() {

			@Override
			public void onClick(ClickEvent event) {
				setProperty(defaultProjectPropertyPath, m_projectName);
				setProperty(defaultWorkflowDefinitionPropertyPath,
						m_workflowDefinitionName);
				HashMap<String, String> valueMap = extractDefaultButtonData();
				m_setDefault.setData(valueMap);
				m_setDefault.setEnabled(false);
			}
		});

		filterPanel.add(workflowDefinitionLabel);
		filterPanel.add(m_workflowDefinitionBox);
		filterPanel.add(m_setDefault);

		m_activity = new FlexTable();
		m_chartPanel = new VerticalPanel();

		horizontal.add(m_activity);
		horizontal.add(m_chartPanel);
		m_activityPanel.setContentWidget((Widget) horizontal);
		m_errorPanel = getUIFactory().createErrorPanel();

		vertical.add(filterPanel);
		vertical.add(m_activityPanel);
		vertical.add(m_errorPanel);

		// Set the panel styles.
		m_activity.setStylePrimaryName(css.activityTable());
		m_chartPanel.setStylePrimaryName(css.chartsPanel());
		m_formatter = m_activity.getFlexCellFormatter();

		getInitialProperties();

		return vertical;
	}

	/**
	 * Update the "Set as Default" button as enabled if user selection is not
	 * default value.
	 */
	private void updateSetDefaultButtonStatus() {
		HashMap<String, String> valueMap = extractDefaultButtonData();
		m_setDefault.detectChangeAndChangeEnable(valueMap);
	}

	/**
	 * get the data from the user selection for project name and workflow
	 * definition
	 * 
	 * @return the data with a HaspMap
	 */
	private HashMap<String, String> extractDefaultButtonData() {
		HashMap<String, String> valueMap = new HashMap<String, String>();
		if (m_projectName != null && !m_projectName.equals(""))
			valueMap.put(defaultProjectPropertyPath, m_projectName);
		if (m_workflowDefinitionName != null
				&& !m_workflowDefinitionName.equals(""))
			valueMap.put(defaultWorkflowDefinitionPropertyPath,
					m_workflowDefinitionName);
		return valueMap;
	}

	/**
	 * Load the initial project name and workflow definition from: 1. the url
	 * parameter 2. the user saved properties if no url parameters specified for
	 * project name and workflow definition and finally load the pipeline table
	 * in to the page.
	 */
	private void getInitialProperties() {
		if (!(m_projectName == null) || !(m_workflowDefinitionName == null)) {
			getInitialProjects();
			return;
		}

		GetPropertyRequest getDefaultProjectPropertyRequest = buildGetPropertyRequest(
				defaultProjectPropertyPath, new DefaultPropertyCallback(this) {
					@Override
					public void handleError(CommanderError error) {
						getLog().debug(
								"Error code=" + error.getCode()
										+ ", Error message="
										+ error.getMessage());
					}

					@Override
					public void handleResponse(Property property) {
						m_projectName = property.getValue();
						getLog().debug("default project is: " + m_projectName);
					}
				});
		GetPropertyRequest getDefaultWorkflowDefinitionPropertyRequest = buildGetPropertyRequest(
				defaultWorkflowDefinitionPropertyPath,
				new DefaultPropertyCallback(this) {
					@Override
					public void handleError(CommanderError error) {
						getLog().debug(
								"Error code=" + error.getCode()
										+ ", Error message="
										+ error.getMessage());
					}

					@Override
					public void handleResponse(Property property) {
						m_workflowDefinitionName = property.getValue();
						getLog().debug(
								"default workflow definiton is: "
										+ m_workflowDefinitionName);
					}
				});

		doRequest(new ChainedCallback() {

			@Override
			public void onComplete() {
				getInitialProjects();
			}

		}, getDefaultProjectPropertyRequest,
				getDefaultWorkflowDefinitionPropertyRequest);

	}

	/**
	 * set the property value to the specified property path.
	 * 
	 * @param propertyPath
	 *            the path of the property
	 * @param propertyValue
	 *            the value to be set
	 */
	public void setProperty(String propertyPath, String propertyValue) {
		SetPropertyRequest setPropReq = getRequestFactory()
				.createSetPropertyRequest();
		setPropReq.setPropertyName(propertyPath);
		setPropReq.setValue(propertyValue);

		PropertyCallback propertyRequestCallback = new DefaultPropertyCallback(
				this) {
			@Override
			public void handleError(CommanderError error) {
				getLog().debug(
						"Error code=" + error.getCode() + ", Error message="
								+ error.getMessage());
			}

			@Override
			public void handleResponse(Property property) {
			}
		};

		setPropReq.setCallback(propertyRequestCallback);
		doRequest(new ChainedCallback() {
			@Override
			public void onComplete() {
				getLog().debug("SetProperty completed!");
			}
		}, setPropReq);
	}

	/**
	 * build the getPropertyRequest
	 * 
	 * @param propertyName
	 *            the property name to request
	 * @param propertyCallback
	 *            the callback to handle the response
	 * @return
	 */
	public GetPropertyRequest buildGetPropertyRequest(String propertyName,
			DefaultPropertyCallback propertyCallback) {
		return getRequestFactory().createGetPropertyRequest()
				.setCallback(propertyCallback).setPropertyName(propertyName);

	}

	/**
	 * update the workflowDefinition dropbox
	 * 
	 * @param projectName
	 *            the project name
	 * @param defaultValue
	 *            the default work flow definition name should be selected and
	 *            displayed
	 */
	private void updateWorkflowDefinition(String projectName,
			final String defaultValue) {
		m_workflowDefinitionBox.clear();

		GetWorkflowDefinitionsRequest wfDefRequest = getRequestFactory()
				.createGetWorkflowDefinitionsRequest()
				.setProjectName(projectName)
				.setCallback(new WorkflowDefinitionListCallback() {

					@Override
					public void handleError(CommanderError error) {
						m_errorPanel.addErrorMessage(error.getMessage());
					}

					@Override
					public void handleResponse(List<WorkflowDefinition> response) {
						m_workflowDefinitionBox.clear();
						for (WorkflowDefinition workflowDefinition : response) {
							m_workflowDefinitionBox.addItem(workflowDefinition
									.getName());
							if (defaultValue.equals(workflowDefinition
									.getName())) {
								m_workflowDefinitionBox.setItemSelected(
										m_workflowDefinitionBox.getItemCount() - 1,
										true);
							}
						}
						if (response.size() > 0) {
							NativeEvent event = Document.get()
									.createChangeEvent();
							DomEvent.fireNativeEvent(event,
									m_workflowDefinitionBox);
						}

					}
				});
		getRequestManager().doRequest(wfDefRequest);
	}

	/**
	 * get all projects and select the default project if it has been specified
	 * in the parameter.
	 */
	private void getInitialProjects() {

		FindObjectsRequest findProjectsRequest = getRequestFactory()
				.createFindObjectsRequest(ObjectType.project)
				.addFilter(new FindObjectsFilter.IsNullFilter("pluginName"))
				.setCallback(new FindObjectsResponseCallback() {

					@Override
					public void handleError(CommanderError error) {
						m_errorPanel.addErrorMessage(error.getMessage());
					}

					@Override
					public void handleResponse(FindObjectsResponse response) {
						for (Project project : response.getProjects()) {
							m_projectBox.addItem(project.getName());
							if (m_projectName.equals(project.getName())) {
								m_projectBox.setItemSelected(
										m_projectBox.getItemCount() - 1, true);
								if (!m_workflowDefinitionName.equals(""))
									updateWorkflowDefinition(project.getName(),
											m_workflowDefinitionName);
							}
						}
					}
				});
		getRequestManager().doRequest(findProjectsRequest);

		HashMap<String, String> valueMap = extractDefaultButtonData();
		m_setDefault.setData(valueMap);
		updateSetDefaultButtonStatus();
		m_setDefault.setEnabled(false);
	}

	/**
	 * Look for "key" and "filter_" GET parameters and store them for the
	 * workflows query to add accordingly.
	 * 
	 * Create the activity panel after adding the filters to the title.
	 */
	private void processUrlParameters() {
		Map<String, List<String>> getParameters = Window.Location
				.getParameterMap();
		String title = "Activity:";
		for (Entry<String, List<String>> stringListEntry : getParameters
				.entrySet()) {
			String name = stringListEntry.getKey();
			List<String> values = stringListEntry.getValue();
			String value = values.isEmpty() ? "" : values.get(0);
			if (name.equals("key")) {
				// Last one wins.
				m_keyProperty = value;
			}
			if (name.startsWith("filter_")) {
				String filter = name.substring(7);
				m_workflowFilters.put(filter, value);
				title += " (" + filter + "=" + value + ")";
			}
		}
		m_activityPanel = getUIFactory().createTitledPanel(title);
	}

	/**
	 * This function gets the state definitions for the GET-specified workflow
	 * definition. If one or more state definition sets the SHOW property, only
	 * their corresponding states will be displayed in the activity table. If no
	 * state definitions set the SHOW property, all states will be displayed.
	 */
	private FindObjectsRequest getStateDefinitionsRequest() {
		FindObjectsRequest stateDefinitionsRequest = getRequestManager()
				.getRequestFactory().createFindObjectsRequest(
						ObjectType.stateDefinition);
		stateDefinitionsRequest.addSort("index", Order.ascending);
		stateDefinitionsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"projectName", m_projectName));
		stateDefinitionsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"workflowDefinitionName", m_workflowDefinitionName));
		stateDefinitionsRequest.addSelect(SHOW, false);
		stateDefinitionsRequest.setCallback(new FindObjectsResponseCallback() {
			@Override
			public void handleError(@NotNull CommanderError error) {
				m_errorPanel.addErrorMessage(error.getMessage());
			}

			/**
			 * Store all the state definitions in one list, and just the ones
			 * that have the SHOW property set in another. If one or more of the
			 * definitions have the SHOW property set, we will only show those
			 * states. Otherwise, we will show them all.
			 */
			@Override
			public void handleResponse(@Nullable FindObjectsResponse response) {
				List<String> allStates = new ArrayList<String>();
				List<String> selectedStates = new ArrayList<String>();
				ListIterator<StateDefinition> iterator = response
						.getStateDefinitions().listIterator();
				while (iterator.hasNext()) {
					StateDefinition stateDefinition = iterator.next();
					allStates.add(stateDefinition.getName());
					String selected = getSelectedProperty(
							response.getSelects(stateDefinition.getObjectId()),
							SHOW);
					if (selected != null && !selected.isEmpty()
							&& !selected.equals("0")) {
						selectedStates.add(stateDefinition.getName());
					}
				}
				m_statesToShow = selectedStates.size() > 0 ? selectedStates
						: allStates;

				// Create the header row for the activity table now that we
				// know which states are going to be included.
				PipelineActivityStyles css = RESOURCES.css();

				m_activity.setWidget(0, 0, null);
				m_formatter.setStylePrimaryName(0, 0, css.activityTableHeader());

				// Create a table for the state names.
				FlexTable pipeline = new FlexTable();
				FlexCellFormatter formatter = pipeline.getFlexCellFormatter();
				int col = 0;
				for (String stateToShow : m_statesToShow) {
					pipeline.setWidget(0, col, new Label(stateToShow));
					formatter.setStylePrimaryName(0, col, css.pipelineHeader());
					col++;

					// Show an arrow unless it's the final state.
					if (m_statesToShow.indexOf(stateToShow) < m_statesToShow
							.size() - 1) {
						pipeline.setWidget(0, col, null);
						formatter.setStylePrimaryName(0, col,
								css.pipelineArrow());
						col++;
					}
				}
				m_activity.setWidget(0, 1, pipeline);
				m_formatter.setStylePrimaryName(0, 1, css.activityTableHeader());

				m_activity.setWidget(0, 2, null);
				m_formatter.setStylePrimaryName(0, 2,
						css.activityTableHeaderLast());
			}
		});

		return stateDefinitionsRequest;
	}

	/**
	 * This function looks for chart definitions on the GET-specified workflow
	 * definition. If they exist, we record the properties being charted to
	 * include when querying for workflows. We also create and store the charts
	 * so the values can be easily added when processing workflows.
	 */
	private GetPropertiesRequest getChartDefinitionsRequest() {
		GetPropertiesRequest getPropertiesRequest = getRequestManager()
				.getRequestFactory().createGetPropertiesRequest();
		getPropertiesRequest.setProjectName(m_projectName);
		getPropertiesRequest
				.setWorkflowDefinitionName(m_workflowDefinitionName);
		getPropertiesRequest.setPath(CHARTS);
		getPropertiesRequest.setRecurse(true);
		getPropertiesRequest.setCallback(new PropertySheetCallback() {

			@Override
			public void handleError(@NotNull CommanderError error) {
				// Ignore non-existent sheets.
				if (!error.getCode()
						.equals(ErrorCode.NoSuchProperty.toString())) {
					m_errorPanel.addErrorMessage(error.getMessage());
				}
			}

			/**
			 * Iterate through all the nested sheets representing individual
			 * charts. Store the properties that need to be added to the query
			 * for workflows. Create the charts and set their title, color,
			 * series labels, etc.
			 */
			@Override
			public void handleResponse(@Nullable PropertySheet response) {
				PipelineActivityStyles css = RESOURCES.css();
				Iterator<Entry<String, Property>> sheets = response
						.getProperties().entrySet().iterator();
				while (sheets.hasNext()) {
					Entry<String, Property> sheet = sheets.next();
					Map<String, Property> properties = sheet.getValue()
							.getPropertySheet().getProperties();
					Iterator<Entry<String, Property>> propertyIterator = properties
							.entrySet().iterator();

					PlotModel model = new PlotModel();
					SimplePlot chart = new SimplePlot(model, new PlotOptions());
					chart.setHeight(CHART_HEIGHT);
					chart.setWidth(CHART_WIDTH);

					// The name of the nested sheet is the chart's title.
					Label title = new Label(sheet.getKey());
					title.setStylePrimaryName(css.chartsTitle());
					m_charts.add(title);

					// Go through each property. When we get to the property
					// name, create a new series. If the label wasn't set, we
					// skip the series. If the color wasn't set, we default it.
					String label = "";
					String color = CHART_DEFAULT_COLOR;
					while (propertyIterator.hasNext()) {
						Entry<String, Property> property = propertyIterator
								.next();
						String name = property.getKey();
						String value = property.getValue().getValue();
						if (name.contains("-color")) {
							color = value;
						}
						if (name.contains("-label")) {
							label = value;
						}
						if (name.contains("-propertyName") && !label.isEmpty()) {
							SeriesHandler series = model
									.addSeries(label, color);
							m_chartProperties.put(value, series);
							label = "";
							color = CHART_DEFAULT_COLOR;
						}
					}
					// Store the chart for now. We can only add it to the panel
					// after all the values from the workflows have been added,
					// otherwise the values won't show up and the charts will be
					// blank.
					m_charts.add(chart);
				}
			}
		});

		return getPropertiesRequest;
	}

	/**
	 * This function gets the workflows to be displayed in the activity table.
	 * 
	 * For each workflow, it passes off control to the getStates function to
	 * make subsequent requests before populating the activity table with the
	 * row corresponding to that workflow.
	 */
	private void getWorkflows() {
		FindObjectsRequest workflowsRequest = getRequestManager()
				.getRequestFactory().createFindObjectsRequest(
						ObjectType.workflow);
		workflowsRequest.addSort("createTime", Order.descending);
		workflowsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"projectName", m_projectName));
		workflowsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"workflowDefinitionName", m_workflowDefinitionName));

		// If a key was specified in the URL, add the corresponding property
		// as a select to the findObjects query.
		if (m_keyProperty != null) {
			workflowsRequest.addSelect(m_keyProperty, false);
		}

		// If any charts were specified, add the properties they're looking for
		// as selects to the findObjects query.
		if (m_chartProperties.size() > 0) {
			Iterator<String> chartProperties = m_chartProperties.keySet()
					.iterator();
			while (chartProperties.hasNext()) {
				String chartProperty = chartProperties.next();
				workflowsRequest.addSelect(chartProperty, false);
			}
		}

		// Add all filters specified in the URL to the request.
		for (String filter : m_workflowFilters.keySet()) {
			workflowsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
					filter, m_workflowFilters.get(filter)));
		}
		workflowsRequest.setMaxIds(20);

		workflowsRequest.setCallback(new FindObjectsResponseCallback() {
			@Override
			public void handleError(@NotNull CommanderError error) {
				m_errorPanel.addErrorMessage(error.getMessage());
			}

			/**
			 * Save the response. For each workflow, pass the control to
			 * getStates to make and process subsequent requests. Also, extract
			 * any specified values needed to build charts.
			 */
			@Override
			public void handleResponse(@Nullable FindObjectsResponse response) {
				m_workflows = response;
				ListIterator<Workflow> iterator = response.getWorkflows()
						.listIterator();
				Integer i = 1;
				while (iterator.hasNext()) {
					Workflow workflow = iterator.next();
					Map<String, Boolean> visitedStates = getVisitedStates(workflow);
					getStates(workflow, visitedStates);
					if (m_chartProperties.size() > 0) {
						getChartValues(workflow, i);
						i++;
					}
				}
				// Now that we've worked through all the workflows, add the
				// charts to the panel.
				Iterator<Widget> charts = m_charts.iterator();
				while (charts.hasNext()) {
					m_chartPanel.add(charts.next());
				}
			}
		});

		getRequestManager().doRequest(workflowsRequest);
	}

	/**
	 * For the given workflow, this function goes through log entries to find
	 * out which states have been visited thus far.
	 *
	 * @param workflow
	 *            -- The workflow whose log entries are being retrieved.
	 */
	private Map<String, Boolean> getVisitedStates(final Workflow workflow) {
		final Map<String, Boolean> visitedStates = new HashMap<String, Boolean>();
		visitedStates.put(workflow.getStartingState(), true);
		FindObjectsRequest logEntriesRequest = getRequestManager()
				.getRequestFactory().createFindObjectsRequest(
						ObjectType.logEntry);
		logEntriesRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"container", "workflow-" + workflow.getId().toString()));
		logEntriesRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"severity", "INFO"));
		logEntriesRequest.addFilter(new FindObjectsFilter.ContainsFilter(
				"message", "to state"));

		logEntriesRequest.setCallback(new FindObjectsResponseCallback() {
			@Override
			public void handleError(@NotNull CommanderError error) {
				m_errorPanel.addErrorMessage(error.getMessage());
			}

			/**
			 * Iterate through the response and for each state, save the state
			 * and collect subsequent requests for manual transitions and
			 * subjobs. Then, execute these collected requests with a final
			 * callback to create a row in the activity table for the given
			 * workflow.
			 */
			@Override
			public void handleResponse(@Nullable FindObjectsResponse response) {
				ListIterator<LogEntry> iterator = response.getLogEntries()
						.listIterator();
				while (iterator.hasNext()) {
					LogEntry entry = iterator.next();
					String message = entry.getMessage();
					RegExp regExp = RegExp.compile("to state '(.*)'");
					MatchResult matcher = regExp.exec(message);
					if (matcher != null) {
						String visitedState = matcher.getGroup(1);
						visitedStates.put(visitedState, true);
					}
				}
			}

		});
		getRequestManager().doRequest(logEntriesRequest);
		return visitedStates;
	}

	/**
	 * For the given workflow, this function gets the relevant states as
	 * determined earlier by the getStateDefinitions function.
	 * 
	 * It stores the states so they can be processed later, and it calls the
	 * getTransitionsAndJobs function for each state to get and store more
	 * information needed to populate the activity table.
	 * 
	 * Once all these requests have been made and processed, it calls the
	 * processWorkflow function to populate the activity table with the row
	 * corresponding to the given workflow.
	 * 
	 * @param workflow
	 *            -- The workflow whose states are being retrieved.
	 */
	private void getStates(final Workflow workflow,
			final Map<String, Boolean> visitedStates) {
		final String workflowName = workflow.getName();

		FindObjectsRequest statesRequest = getRequestManager()
				.getRequestFactory().createFindObjectsRequest(ObjectType.state);
		statesRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"projectName", m_projectName));
		statesRequest.addFilter(new FindObjectsFilter.EqualsFilter(
				"workflowName", workflowName));

		statesRequest.setCallback(new FindObjectsResponseCallback() {
			@Override
			public void handleError(@NotNull CommanderError error) {
				m_errorPanel.addErrorMessage(error.getMessage());
			}

			/**
			 * Iterate through the response and for each state, save the state
			 * and collect subsequent requests for manual transitions and
			 * subjobs. Then, execute these collected requests with a final
			 * callback to create a row in the activity table for the given
			 * workflow.
			 */
			@Override
			public void handleResponse(@Nullable FindObjectsResponse response) {
				ListIterator<State> iterator = response.getStates()
						.listIterator();
				List<CommanderRequest<?>> requests = new ArrayList<CommanderRequest<?>>();
				while (iterator.hasNext()) {
					State state = iterator.next();
					String stateName = state.getName();
					m_stateMap.put(workflowName + "/" + stateName, state);
					// We only need to get manual transition and/or subjob info
					// for states which are shown in the table and the current
					// active state.
					if (m_statesToShow.contains(stateName) || state.isActive()) {
						requests.addAll(getTransitionsAndJobs(workflow, state));
					}
				}
				getRequestManager().doRequest(new ChainedCallback() {
					/**
					 * Once all of the manual transition and subjob requests are
					 * complete and the results have been stored, we have all
					 * the info we need to add the given workflow to the
					 * activity table, which is what the processWorkflow method
					 * will do.
					 */
					@Override
					public void onComplete() {
						processWorkflow(workflow, visitedStates);
					}
				}, requests);
			}

		});

		getRequestManager().doRequest(statesRequest);
	}

	/**
	 * For the given state, this function gets and stores outbound manual
	 * transition and subjob information to be processed later by the
	 * processWorkflow function.
	 * 
	 * @param workflow
	 *            -- The workflow to which the state belongs.
	 * @param state
	 *            -- The state whose transition and job info is retreived.
	 */
	private List<CommanderRequest<?>> getTransitionsAndJobs(Workflow workflow,
			State state) {
		String stateName = state.getName();
		final String workflowName = state.getWorkflowName();
		final String mapIndex = workflowName + "/" + stateName;

		List<CommanderRequest<?>> requests = new ArrayList<CommanderRequest<?>>();

		// Only get manual transitions for the active state of a non-completed
		// workflow.
		if (!workflow.isCompleted() && state.isActive()) {
			FindObjectsRequest transitionsRequest = getRequestManager()
					.getRequestFactory().createFindObjectsRequest(
							ObjectType.transition);
			transitionsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
					"projectName", m_projectName));
			transitionsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
					"workflowName", workflowName));
			transitionsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
					"stateName", stateName));
			transitionsRequest.addFilter(new FindObjectsFilter.EqualsFilter(
					"trigger", "manual"));

			transitionsRequest.setCallback(new FindObjectsResponseCallback() {
				@Override
				public void handleError(@NotNull CommanderError error) {
					m_errorPanel.addErrorMessage(error.getMessage());
				}

				/**
				 * Just store the result for now; processWorkflow will use it
				 * later on.
				 */
				@Override
				public void handleResponse(
						@Nullable FindObjectsResponse response) {
					m_manualTransitionMap.put(workflowName,
							response.getTransitions());
				}

			});
			requests.add(transitionsRequest);
		}

		// Only get subjob information for states which we're displaying that
		// actually have a subjob.
		if (m_statesToShow.contains(stateName) && !state.getSubjob().isEmpty()) {
			GetJobInfoRequest jobRequest = getRequestManager()
					.getRequestFactory().createGetJobInfoRequest();
			jobRequest.setJobId(state.getSubjob());

			jobRequest.setCallback(new JobCallback() {
				@Override
				public void handleError(@NotNull CommanderError error) {
					m_errorPanel.addErrorMessage(error.getMessage());
				}

				/**
				 * Just store the result for now; processWorkflow will use it
				 * later on.
				 */
				@Override
				public void handleResponse(@Nullable Job response) {
					m_subJobMap.put(mapIndex, response);
				}
			});
			requests.add(jobRequest);
		}

		return requests;
	}

	/**
	 * This function takes all the information collected by the "get" functions
	 * and populates a row in the activity table for the given workflow.
	 * 
	 * @param workflow
	 *            -- The workflow to process and add a row to the activity
	 *            table.
	 */
	private void processWorkflow(Workflow workflow,
			Map<String, Boolean> visitedStates) {
		String workflowName = workflow.getName();
		String activeState = workflow.getActiveState();
		Integer row = m_activity.getRowCount();
		PipelineActivityStyles css = RESOURCES.css();
		Boolean even = row % 2 == 0;
		String style = even ? css.activityTableEven() : css.activityTableOdd();

		// First, create a string for the first column. If a key was specified
		// as a GET parameter in the URL, get the selected property value for
		// that key and display it. Otherwise, default to the workflow name.
		String key = null;
		if (m_keyProperty != null) {
			Map<String, Property> selects = m_workflows.getSelects(workflow
					.getObjectId());
			String selected = getSelectedProperty(selects, m_keyProperty);
			if (selected != null && !selected.isEmpty()) {
				key = selected;
			}
		}
		if (key == null) {
			key = workflowName;
		}
		Anchor nameLink = new Anchor();
		nameLink.setHref(createWorkflowLink(workflow));
		nameLink.setTarget("_blank");
		nameLink.setText(key);
		m_activity.setWidget(row, 0, nameLink);
		m_formatter.setStylePrimaryName(row, 0, style);

		// Go through each state and graphically display the current subjob
		// status.

		FlexTable pipeline = new FlexTable();
		FlexCellFormatter formatter = pipeline.getFlexCellFormatter();
		int col = 0;
		for (String stateName : m_statesToShow) {
			Boolean active = stateName.equals(activeState);
			pipeline.setWidget(0, col, null);
			String mapIndex = workflowName + "/" + stateName;
			String backgroundStyle;
			if (m_subJobMap.containsKey(mapIndex)) {
				Job job = m_subJobMap.get(mapIndex);
				formatter.getElement(0, col).setAttribute("onclick",
						"window.open('" + createJobLink(job) + "');");

				JobOutcome outcome = job.getOutcome();
				if (outcome.equals(JobOutcome.success)) {
					backgroundStyle = active ? css.pipelineSuccessActive()
							: css.pipelineSuccess();
				} else if (outcome.equals(JobOutcome.warning)) {
					backgroundStyle = active ? css.pipelineWarningActive()
							: css.pipelineWarning();
				} else {
					backgroundStyle = active ? css.pipelineErrorActive() : css
							.pipelineError();
				}
			} else if (visitedStates.containsKey(stateName)) {
				backgroundStyle = active ? css.pipelineNoJobVisitedActive()
						: css.pipelineNoJobVisited();
			} else {
				backgroundStyle = css.pipelineNoJob();
			}
			formatter.setStylePrimaryName(0, col, backgroundStyle);
			col++;

			// Show an arrow unless it's the final state.
			if (m_statesToShow.indexOf(stateName) < m_statesToShow.size() - 1) {
				pipeline.setWidget(0, col, null);
				formatter.setStylePrimaryName(0, col, css.pipelineArrow());
				col++;
			}
		}
		m_activity.setWidget(row, 1, pipeline);
		m_formatter.setStylePrimaryName(row, 1, style);

		// Finally, create the actions for this workflow. This is basically the
		// standard view action along with any available manual transitions.
		FlexTable actions = new FlexTable();
		formatter = actions.getFlexCellFormatter();
		col = 0;
		Anchor viewLink = new Anchor();
		viewLink.setHref(createWorkflowLink(workflow));
		viewLink.setTarget("_blank");
		viewLink.setText("View");
		actions.setWidget(0, col, viewLink);
		col++;
		if (m_manualTransitionMap.containsKey(workflowName)) {
			ListIterator<Transition> iterator = m_manualTransitionMap.get(
					workflowName).listIterator();
			while (iterator.hasNext()) {
				actions.setWidget(0, col, new Label("|"));
				formatter.setStylePrimaryName(0, col, css.actionsSeparator());
				col++;

				Transition transition = iterator.next();
				Anchor transitionLink = new Anchor();
				transitionLink.setHref(createTransitionLink(transition));
				transitionLink.setTarget("_blank");
				transitionLink.setText(transition.getName());
				actions.setWidget(0, col, transitionLink);
				col++;
			}
		}
		m_activity.setWidget(row, 2, actions);
		m_formatter
				.setStylePrimaryName(row, 2, even ? css.activityTableEvenLast()
						: css.activityTableOddLast());
	}

	/**
	 * For a single workflow, get all the property values that need to be
	 * charted and add them to the appropriate series.
	 */
	private void getChartValues(final Workflow workflow, Integer i) {
		Iterator<String> iterator = m_chartProperties.keySet().iterator();
		Map<String, Property> selects = m_workflows.getSelects(workflow
				.getObjectId());

		while (iterator.hasNext()) {
			String property = iterator.next();
			String value = getSelectedProperty(selects, property);
			if (value != null) {
				m_chartProperties.get(property).add(
						new DataPoint(i, Double.parseDouble(value)));
			}
		}
	}

	/**
	 * Helper functions.
	 */

	private static String getSelectedProperty(Map<String, Property> selects,
			String propertyName) {
		Property property;
		if (selects == null) {
			property = null;
		} else {
			property = selects.get(propertyName);
		}
		if (property == null) {
			return null;
		}
		return property.getValue();
	}

	private static String createWorkflowLink(Workflow workflow) {
		return CommanderUrlBuilder.createLinkUrl("workflowDetails", "projects",
				workflow.getProjectName(), "workflows", workflow.getName())
				.buildString()
				+ "?s=Workflows";
	}

	private static String createJobLink(Job job) {
		return CommanderUrlBuilder.createLinkUrl("jobDetails", "jobs",
				job.getName()).buildString()
				+ "?s=Jobs";
	}

	private static String createTransitionLink(Transition transition) {
		return CommanderUrlBuilder.createLinkUrl("transitionWorkflow",
				"projects", transition.getProjectName(), "workflows",
				transition.getWorkflowName(), "states",
				transition.getStateName(), "transitions", transition.getName())
				.buildString()
				+ "?s=Workflows";
	}
}
