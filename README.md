OVERVIEW:

The "PipelineActivity" ElectricCommander/ElectricFlow plugin provides a pipeline dashboard to view workflow instances.

USAGE:

You must first install and promote the plugin (available in out/EC-PipelineActivity.jar). You can then use the following URL to access the dashboard:

https://SERVER/commander/pages/EC-PipelineActivity/activity?projectName=PROJECT&workflowDefinitionName=WORKFLOWDEFINITION

Add URL GET parameters similar to the following examples:
* filter_version=1.0.0 -- only show workflows where the property "version" equals "1.0.0". Add as many of these as you like. You can match built-in or custom properties as long as they are top-level properties of the workflows.
* key_Revision=revision -- rename the first column from "Name" to "Revision". The clickable link will be based on the value of the property "revision" instead of the workflow name. You can only set this parameter once. You can match a built-in or a custom property as long as it is a top-level property of the workflows.

By default, the dashboard will show all states in the workflow in the order defined on the workflow definition.
* If you want to filter which states show up, set the property pipeline_activity_show to 1 on all state definitions that you want to show up.
* The "left to right" order for states is the same as the "top to bottom" order for state definitions in the "List" view of the workflow definition. You can reorder that list by using the "Move" action.

To add charts on the right, create a property sheet on the workflow definition called "pipeline_activity", and another property sheet within it called "charts". In that, create a nested sheet for each chart (the name of the sheet is the title of the chart). Within the sheet, you can add any number of series. For each series, create the following properties (starting with the number 1):
* series1-color -- a hex value representing the color (e.g. #008800). It'll default to black if unspecified. There are several online tools to pick hex values (e.g. http://www.w3schools.com/tags/ref_colorpicker.asp)
* series1-label -- the label shown in the legend for the series
* series1-propertyName -- the property to chart (you can match a built-in or a custom property as long as it is a top-level property of the workflows)

SOURCES:

The sources are available in the src directory. They were built using the Commander SDK v2.0. The documentation for the SDK is available at http://docs.electric-cloud.com.

AUTHOR:

Tanay Nagjee, Electric Cloud Solutions Engineer
tanay@electric-cloud.com

LEGAL:

This module is not officially supported by Electric Cloud. It has undergone no  formal testing and you may run into issues that have not been uncovered in the limited manual testing done so far. Electric Cloud should not be held liable for any repercussions of using this software.