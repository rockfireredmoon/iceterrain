package org.iceterrain;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.Icelib;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icescene.configuration.TerrainClutterConfiguration;
import org.icescene.props.AbstractProp;
import org.icescene.ui.PreviewModelView;
import org.iceui.controls.ElementStyle;

import com.jme3.app.state.AppStateManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapFont.Align;
import com.jme3.font.BitmapFont.VAlign;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;

import icemoon.iceloader.ServerAssetManager;
import icetone.controls.buttons.PushButton;
import icetone.controls.containers.Frame;
import icetone.controls.containers.OSRViewPort;
import icetone.controls.lists.FloatRangeSpinnerModel;
import icetone.controls.lists.Spinner;
import icetone.controls.table.Table;
import icetone.controls.table.TableCell;
import icetone.controls.table.TableRow;
import icetone.controls.text.Label;
import icetone.core.BaseElement;
import icetone.core.Form;
import icetone.core.Orientation;
import icetone.core.Size;
import icetone.core.StyledContainer;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.mig.MigLayout;
import icetone.extras.appstates.FrameManagerAppState;
import icetone.extras.chooser.ChooserDialog;
import icetone.extras.chooser.StringChooserModel;
import icetone.extras.windows.PersistentWindow;
import icetone.extras.windows.SaveType;
import icetone.fontawesome.FontAwesome;

/**
 * For editing clutter definitions
 */
public class ClutterDefinitionEditorAppState extends IcemoonAppState<TerrainEditorAppState> {

	private static final Logger LOG = Logger.getLogger(ClutterDefinitionEditorAppState.class.getName());
	private PersistentWindow clutterDefEditWindow;
	private Spinner<Float> density;
	private TerrainClutterConfiguration clutterConfiguration;
	private Table modelsTable;
	private String splatImageName;
	private TerrainClutterConfiguration.ClutterDefinition def;
	private PushButton addButton;
	private PushButton removeButton;
	private PushButton saveButton;

	public ClutterDefinitionEditorAppState(Preferences prefs) {
		super(prefs);
	}

	@Override
	protected TerrainEditorAppState onInitialize(AppStateManager stateManager, IcesceneApp app) {
		return stateManager.getState(TerrainEditorAppState.class);
	}

	@Override
	protected void postInitialize() {
		// Window
		clutterDefEditWindow = new PersistentWindow(screen, SceneConfig.CLUTTER_DEFINITION, VAlign.Bottom, Align.Right,
				new Size(200, 340), true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				app.getStateManager().detach(ClutterDefinitionEditorAppState.this);
			}
		};
		clutterDefEditWindow.setMinDimensions(new Size(clutterDefEditWindow.getDimensions()));
		clutterDefEditWindow.setDestroyOnHide(true);
		clutterDefEditWindow.setWindowTitle("Clutter");

		// Window management (if available)
		FrameManagerAppState win = stateManager.getState(FrameManagerAppState.class);
		if (win != null) {
			clutterDefEditWindow.setMinimizable(true);
			clutterDefEditWindow.setMaximizable(true);
		}

		// Layout
		BaseElement contentArea = clutterDefEditWindow.getContentArea();
		contentArea.setLayoutManager(
				new MigLayout(screen, "wrap 2, fill", "[shrink 0][]", "[shrink 0][fill, grow][shrink 0]"));
		Form f = new Form(screen);

		// Density
		contentArea.addElement(new Label("Density", screen));
		density = new Spinner<Float>(screen, Orientation.HORIZONTAL, true);
		density.onChange(evt -> def.setDensity(evt.getNewValue()));
		density.setSpinnerModel(new FloatRangeSpinnerModel(0, 1, .1f, 0));
		f.addFormElement(density);
		contentArea.addElement(density);

		// Models
		modelsTable = new Table(screen);
		modelsTable.setUseContentPaging(false);
		modelsTable.onChanged(evt -> setAvailable());
		modelsTable.setHeadersVisible(false);
		modelsTable.addColumn("Model");
		modelsTable.addColumn("Density");
		modelsTable.setColumnResizeMode(Table.ColumnResizeMode.AUTO_ALL);
		contentArea.addElement(modelsTable, "span 2, growx, growy");

		// Find all the clutter props
		final Set<String> csmXmls = ((ServerAssetManager) app.getAssetManager())
				.getAssetNamesMatching("Prop/Prop-Clutter.*/.*\\.csm.xml");

		// Add
		addButton = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		addButton.onMouseReleased(evt -> {
			if (def == null) {
				def = new TerrainClutterConfiguration.ClutterDefinition(splatImageName);
				TerrainClutterConfiguration.get(assetManager).putClutterDefinition(splatImageName, def);
				ElementStyle.normalColor(clutterDefEditWindow.getDragBar());
			}
			ChooserDialog<String> chooser = new ChooserDialog<String>(screen, null, "Choose Model",
					new StringChooserModel(csmXmls), prefs,
					new PreviewModelView(screen, ClutterDefinitionEditorAppState.this.getParent().getPropFactory()));
			chooser.onChange(xevt -> {
				if (!xevt.isTemporary() && xevt.getNewValue() != null) {
					final String meshName = Icelib.getBasename(Icelib.getFilename(xevt.getNewValue())) + ".mesh";
					def.getMeshScales().put(meshName, 0.1f);
					addRow(meshName, 0.1f, false);
					chooser.hide();
				}
			});
			chooser.setModal(true);
			screen.addElement(chooser, ScreenLayoutConstraints.center);
		});
		addButton.setText("Add");
		FontAwesome.PLUS.button(24, addButton);

		// Remove
		removeButton = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		removeButton.onMouseReleased(evt -> {
			TableRow row = modelsTable.getSelectedRow();
			if (row != null) {
				modelsTable.removeRow(row);
				def.getMeshScales().remove((String) row.getCell(0).getValue());
				setAvailable();
			}
		});
		removeButton.setText("Remove");
		FontAwesome.TRASH.button(24, removeButton);

		// Save
		saveButton = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		saveButton.onMouseReleased(evt -> {
			try {
				clutterConfiguration.putClutterDefinition(splatImageName, def);
				File file = Icelib.makeParent(ClutterDefinitionEditorAppState.this.app.getAssets()
						.getExternalAssetFile(clutterConfiguration.getAssetPath()));
				LOG.info(String.format("Writing %s to %s", splatImageName, file));
				FileOutputStream fos = new FileOutputStream(file);
				try {
					clutterConfiguration.write(fos);
				} finally {
					fos.close();
				}
				info(String.format("Saved clutter definition %s to %s", splatImageName,
						Icelib.getFilename(file.getPath())));
				ClutterAppState cas = app.getStateManager().getState(ClutterAppState.class);
				TerrainClutterConfiguration.remove();
				if (cas != null) {
					cas.timedReload();
				}
			} catch (IOException ioe) {
				error("Failed to save environment.", ioe);
				LOG.log(Level.SEVERE, "Failed to save environment.", ioe);
			}
		});
		saveButton.setText("Save");
		FontAwesome.SAVE.button(24, saveButton);

		// Buttons
		StyledContainer modelButtons = new StyledContainer(screen);
		modelButtons.setLayoutManager(new MigLayout(screen, "fill, gap 0, ins 0", "[al 50%][al 50%][al 50%]", "[]"));
		modelButtons.addElement(addButton);
		modelButtons.addElement(removeButton);
		modelButtons.addElement(saveButton);
		contentArea.addElement(modelButtons, "span 2, growx");

		// Pack and show
		setAvailable();
		clutterDefEditWindow.setResizable(true);
		clutterDefEditWindow.sizeToContent();
		screen.showElement(clutterDefEditWindow);

		// Clutter configuration appstate
		clutterConfiguration = TerrainClutterConfiguration.get(assetManager);

		// If the split image name has been set, load the clutter definition for
		// it now
		if (splatImageName != null) {
			new Thread("LoadClutterModels") {
				@Override
				public void run() {
					setSplatImageName(splatImageName, true);
				}
			}.start();
		}

	}

	public void setSplatImageName(final String splatImageName, boolean queueUpdate) {
		if (queueUpdate && clutterDefEditWindow != null
				&& clutterDefEditWindow.getState().equals(Frame.State.MINIMIZED)) {
			app.enqueue(new Callable<Void>() {
				public Void call() throws Exception {
					clutterDefEditWindow.restore();
					return null;
				}
			});
		}
		this.splatImageName = splatImageName;
		if (modelsTable != null) {
			// First set the table and other basics
			def = splatImageName == null ? null : clutterConfiguration.getClutterDefinition(splatImageName);
			if (def != null) {
				def = def.clone();
			}
			if (queueUpdate) {
				try {
					app.enqueue(new Callable<Void>() {
						public Void call() throws Exception {
							resetClutterTable();
							return null;
						}
					}).get();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			} else {
				resetClutterTable();
			}

			// Now load the models
			if (def != null) {
				for (Map.Entry<String, Float> en : def.getMeshScales().entrySet()) {
					addRow(en.getKey(), en.getValue(), queueUpdate);
				}
			}

		}
	}

	@Override
	protected void onCleanup() {
		clutterDefEditWindow.hide();
	}

	private void resetClutterTable() {
		modelsTable.removeAllRows();
		if (splatImageName != null) {
			clutterDefEditWindow.setWindowTitle(String.format("Clutter (%s)", splatImageName));
			if (def == null) {
				ElementStyle.errorColor(clutterDefEditWindow.getDragBar());
			} else {
				ElementStyle.normalColor(clutterDefEditWindow.getDragBar());
				density.setSelectedValue(def.getDensity());
			}
		} else {
			clutterDefEditWindow.setWindowTitle("Clutter");
		}
	}

	private void setAvailable() {
		final boolean selEmpty = modelsTable.getSelectedRows().isEmpty();
		removeButton.setEnabled(!selEmpty);
		saveButton.setEnabled(def != null);
	}

	private void addRow(final String meshName, float density, boolean queue) {

		final String propName = String.format("Prop-Clutter1#%s", Icelib.getBasename(meshName));
		final AbstractProp prop = parent.getPropFactory().getProp(propName);

		if (queue) {
			app.run(new Runnable() {
				public void run() {
					addPropRow(meshName, density, prop, propName, queue);
				}
			});
		} else {
			addPropRow(meshName, density, prop, propName, queue);
		}
	}

	private void addPropRow(final String meshName, float density, AbstractProp prop, String propName, boolean queue) {

		prop.getSpatial().rotate(0, -FastMath.HALF_PI, 0);
		prop.getSpatial().scale(0.25f);
		// Model
		final TableCell cell1 = new TableCell(screen, meshName, meshName);
		cell1.setPreferredDimensions(null);
		cell1.setTextVAlign(BitmapFont.VAlign.Bottom);
		cell1.setTextAlign(BitmapFont.Align.Center);

		// Density
		final TableCell cell2 = new TableCell(screen, null, density);
		cell2.setHAlign(BitmapFont.Align.Center);

		Spinner<Float> itemDensity = new Spinner<Float>(screen, Orientation.HORIZONTAL, true);
		itemDensity.onChange(evt -> def.getMeshScales().put(meshName, evt.getNewValue()));
		itemDensity.setSpinnerModel(new FloatRangeSpinnerModel(0, 10f, 0.1f, density));
		cell2.addElement(itemDensity);

		doAddRow(cell1, cell2, propName, prop);
	}

	private void doAddRow(TableCell cell1, TableCell cell2, String propName, AbstractProp prop) {

		Node n = new Node();
		// AmbientLight al = new AmbientLight();
		// al.setColor(ColorRGBA.White.mult(3));
		// n.addLight(al);
		n.attachChild(prop.getSpatial());

		OSRViewPort vp = new OSRViewPort(screen, new Size(100, 100));
		vp.setOSRBridge(n, 100, 100);
		vp.setIgnoreMouse(true);
		cell1.addElement(vp);

		final TableRow row = new TableRow(screen, modelsTable);
		row.addElement(cell1);
		row.addElement(cell2);
		row.setToolTipText(Icelib.getBasename(Icelib.getFilename(propName)));
		modelsTable.addRow(row);

		setAvailable();

		// Must do after added to scene
		AmbientLight al = new AmbientLight();
		al.setColor(ColorRGBA.White.mult(1));
		n.addLight(al);

	}
}
