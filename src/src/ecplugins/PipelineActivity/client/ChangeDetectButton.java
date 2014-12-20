package ecplugins.PipelineActivity.client;

import java.util.HashMap;
import java.util.Set;

import com.google.gwt.user.client.ui.Button;

/**
 * This class can be used to check if some data has changed, then set the button
 * as enabled and clickable, otherwise make it disabled.
 * 
 * @author swen
 *
 */
public class ChangeDetectButton extends Button {

	private HashMap<String, String> innerStateData = new HashMap<String, String>();

	public ChangeDetectButton(String html) {
		super(html);
	}

	/**
	 * update the internal state data.
	 * @param initData the data
	 */
	public void setData(HashMap<String, String> initData) {
		innerStateData = initData;
	}

	/**
	 * check if input data has anything different with the internal data, if Yes, enable the button, 
	 * otherwise disable it.
	 * @param data
	 */
	public void detectChangeAndChangeEnable(HashMap<String, String> data) {
		Set<String> keySet = data.keySet();
		boolean changed = false;
		for (String key : keySet) {
			if (innerStateData.get(key) == null || !innerStateData.get(key).equals(data.get(key))) {
				changed = true;
			}
		}

		this.setEnabled(changed);
	}
}
