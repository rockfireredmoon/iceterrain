package org.iceterrain;

import java.util.Collection;
import java.util.logging.Level;
import java.util.prefs.Preferences;

import org.apache.commons.io.FilenameUtils;
import org.icelib.Icelib;
import org.iceui.controls.ImageFieldControl;

import icetone.controls.buttons.PushButton;
import icetone.core.BaseScreen;
import icetone.core.StyledContainer;
import icetone.core.ToolKit;
import icetone.core.layout.mig.MigLayout;
import icetone.extras.chooser.StringChooserModel;
import icetone.fontawesome.FontAwesome;

public class SplatControl extends StyledContainer {

	private final ImageFieldControl texture;
	private final PushButton editClutterDef;

	public SplatControl(BaseScreen screen, Preferences prefs, Collection<String> imageResources) {
		super(screen);

		setLayoutManager(new MigLayout(screen, "ins 0, gap 1, fill", "[grow][shrink 0]", "[shrink 0]"));
		texture = new ImageFieldControl(screen, null, new StringChooserModel(imageResources), prefs) {
			@Override
			protected void onResourceChosen(String newResource) {
				try {
					onChange(FilenameUtils.getFullPathNoEndSeparator(newResource), newResource);
				} catch (Exception e) {
					TerrainEditorAppState.LOG.log(Level.SEVERE, "Failed to select texture. %s", e);
				}
			}
		};
		addElement(texture, "growx");

		editClutterDef = new PushButton(screen) {
			{
				setStyleClass("edit-clutter chooser-button");
			}
		};
		FontAwesome.LEAF.button(16, editClutterDef);
		editClutterDef.onMouseReleased(evt -> {
			ClutterDefinitionEditorAppState defe = ToolKit.get().getApplication().getStateManager()
					.getState(ClutterDefinitionEditorAppState.class);
			if (defe == null) {
				defe = new ClutterDefinitionEditorAppState(prefs);
				ToolKit.get().getApplication().getStateManager().attach(defe);
			}
			new Thread("LoadClutterModels") {
				@Override
				public void run() {
					ToolKit.get().getApplication().getStateManager().getState(ClutterDefinitionEditorAppState.class)
							.setSplatImageName(Icelib.getFilename(texture.getValue()), true);
				}
			}.start();
		});
		editClutterDef.setToolTipText("Edit Clutter Definition");
		addElement(editClutterDef, "growy");
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