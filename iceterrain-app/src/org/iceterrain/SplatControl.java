package org.iceterrain;

import java.util.Collection;
import java.util.logging.Level;
import java.util.prefs.Preferences;

import org.apache.commons.io.FilenameUtils;
import org.icelib.Icelib;
import org.iceui.controls.ImageFieldControl;

import com.jme3.input.event.MouseButtonEvent;

import icetone.controls.buttons.ButtonAdapter;
import icetone.core.Container;
import icetone.core.ElementManager;
import icetone.core.layout.mig.MigLayout;

public class SplatControl extends Container {

	private final ImageFieldControl texture;
	private final ButtonAdapter editClutterDef;

	public SplatControl(ElementManager screen, Preferences prefs, Collection<String> imageResources) {
		super(screen);
		
		setLayoutManager(new MigLayout(screen, "ins 0, gap 1, fill", "[grow][shrink 0]", "[shrink 0]"));
		texture = new ImageFieldControl(screen, null, imageResources, prefs) {
			@Override
			protected void onResourceChosen(String newResource) {
				try {
					onChange(FilenameUtils.getFullPathNoEndSeparator(newResource), newResource);
				} catch (Exception e) {
					TerrainEditorAppState.LOG.log(Level.SEVERE, "Failed to select texture. %s", e);
				}
			}
		};
		addChild(texture, "growx");

		editClutterDef = new ButtonAdapter(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				ClutterDefinitionEditorAppState defe = app.getStateManager().getState(ClutterDefinitionEditorAppState.class);
				if (defe == null) {
					defe = new ClutterDefinitionEditorAppState(prefs);
					app.getStateManager().attach(defe);
				}
				new Thread("LoadClutterModels") {
					@Override
					public void run() {
						app.getStateManager().getState(ClutterDefinitionEditorAppState.class)
								.setSplatImageName(Icelib.getFilename(texture.getValue()), true);
					}
				}.start();
			}
		};
		editClutterDef.setToolTipText("Edit Clutter Definition");
		// editClutterDef.setStyles("PickerButton");
		editClutterDef.setButtonIcon(10, 10, "BuildIcons/Icon-32-Build-Clutter.png");
		addChild(editClutterDef);
	}

	public String getValue() {
		return texture.getValue();
	}

	public void setValue(String string) {
		texture.setValue(string);
	}

	public void setValueWithCallback(String string) {
		texture.setValueWithCallback(string);
	}

	protected void onChange(String dir, String newResource) {
	}
}