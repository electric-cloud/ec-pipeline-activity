
package ecplugins.PipelineActivity.client;

import com.electriccloud.commander.gwt.client.ComponentContext;

import com.electriccloud.commander.gwt.client.Component;
import com.electriccloud.commander.gwt.client.ComponentBaseFactory;

/**
 * This factory is responsible for providing instances of the PipelineActivityComponent
 * class.
 */
public class PipelineActivityComponentFactory
    extends ComponentBaseFactory
{

    @Override
    protected Component createComponent(ComponentContext jso) {
        return new PipelineActivityComponent();
    }
}
