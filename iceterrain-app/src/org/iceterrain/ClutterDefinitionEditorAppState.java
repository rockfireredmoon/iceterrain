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
import org.icescene.ui.WindowManagerAppState;
import org.iceui.HPosition;
import org.iceui.VPosition;
import org.iceui.controls.ElementStyle;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyPersistentWindow;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.SaveType;
import org.iceui.controls.chooser.ChooserDialog;

import com.jme3.app.state.AppStateManager;
import com.jme3.font.BitmapFont;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Node;

import icemoon.iceloader.ServerAssetManager;
import icetone.controls.extras.OSRViewPort;
import icetone.controls.form.Form;
import icetone.controls.lists.FloatRangeSpinnerModel;
import icetone.controls.lists.Spinner;
import icetone.controls.lists.Table;
import icetone.controls.lists.Table.TableCell;
import icetone.controls.text.Label;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.Element.Orientation;
import icetone.core.layout.mig.MigLayout;

/**
 * For editing clutter definitions
 */
public class ClutterDefinitionEditorAppState extends IcemoonAppState<TerrainEditorAppState> {

	private static final Logger LOG = Logger.getLogger(ClutterDefinitionEditorAppState.class.getName());
	private FancyPersistentWindow clutterDefEditWindow;
	private Spinner<Float> density;
	private TerrainClutterConfiguration clutterConfiguration;
	private Table modelsTable;
	private String splatImageName;
	private TerrainClutterConfiguration.ClutterDefinition def;
	private FancyButton addButton;
	private FancyButton removeButton;
	private FancyButton saveButton;

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
		clutterDefEditWindow = new FancyPersistentWindow(screen, SceneConfig.CLUTTER_DEFINITION,
				screen.getStyle("Common").getInt("defaultWindowOffset"), VPosition.BOTTOM, HPosition.RIGHT, new Vector2f(200, 340),
				FancyWindow.Size.SMALL, true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				app.getStateManager().detach(ClutterDefinitionEditorAppState.this);
			}
		};
		clutterDefEditWindow.setMinDimensions(clutterDefEditWindow.getOrgDimensions());
		clutterDefEditWindow.setDestroyOnHide(true);
		clutterDefEditWindow.setWindowTitle("Clutter");

		// Window management (if available)
		WindowManagerAppState win = stateManager.getState(WindowManagerAppState.class);
		if (win != null) {
			clutterDefEditWindow.setMinimizable(true);
			clutterDefEditWindow.setMaximizable(true);
		}

		// Layout
		Element contentArea = clutterDefEditWindow.getContentArea();
		contentArea.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[shrink 0][]", "[shrink 0][fill, grow][shrink 0]"));
		Form f = new Form(screen);

		// Density
		contentArea.addChild(new Label("Density", screen));
		density = new Spinner<Float>(screen, Orientation.HORIZONTAL, true) {
			@Override
			public void onChange(Float value) {
				def.setDensity(value);
			}
		};
		density.setSpinnerModel(new FloatRangeSpinnerModel(0, 1, .1f, 0));
		f.addFormElement(density);
		contentArea.addChild(density);

		// Models
		modelsTable = new Table(screen) {
			@Override
			public void onChange() {
				setAvailable();
			}
		};
		modelsTable.setHeadersVisible(false);
		modelsTable.addColumn("Model");
		modelsTable.addColumn("Density");
		modelsTable.setColumnResizeMode(Table.ColumnResizeMode.AUTO_ALL);
		contentArea.addChild(modelsTable, "span 2, growx, growy");

		// Find all the clutter props
		final Set<String> csmXmls = ((ServerAssetManager) app.getAssetManager())
				.getAssetNamesMatching("Prop/Prop-Clutter/.*\\.csm.xml");

		// Add
		addButton = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				if (def == null) {
					def = new TerrainClutterConfiguration.ClutterDefinition(splatImageName);
					TerrainClutterConfiguration.get(assetManager).putClutterDefinition(splatImageName, def);
					ElementStyle.normalColor(screen, clutterDefEditWindow.getDragBar());
				}
				ChooserDialog chooser = new ChooserDialog(screen, "Choose Model", csmXmls, prefs,
						new PreviewModelView(screen, ClutterDefinitionEditorAppState.this.getParent().getPropFactory())) {
					@Override
					public boolean onChosen(String path) {
						if (path != null) {
							final String meshName = Icelib.getBasename(Icelib.getFilename(path)) + ".mesh";
							def.getMeshScales().put(meshName, 0.1f);
							addRow(meshName, 0.1f, false);
						}
						return true;
					}
				};
				chooser.sizeToContent();
				screen.addElement(chooser);
			}
		};
		addButton.setText("Add");

		// Remove
		removeButton = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				Table.TableRow row = modelsTable.getSelectedRow();
				if (row != null) {
					modelsTable.removeRow(row);
					def.getMeshScales().remove((String) row.getCell(0).getValue());
					setAvailable();
				}
			}
		};
		removeButton.setText("Remove");

		// Save
		saveButton = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
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
					info(String.format("Saved clutter definition %s to %s", splatImageName, Icelib.getFilename(file.getPath())));
					ClutterAppState cas = app.getStateManager().getState(ClutterAppState.class);
					TerrainClutterConfiguration.remove();
					if (cas != null) {
						cas.timedReload();
					}
				} catch (IOException ioe) {
					error("Failed to save environment.", ioe);
					LOG.log(Level.SEVERE, "Failed to save environment.", ioe);
				}
			}
		};
		saveButton.setText("Save");

		// Buttons
		Container modelButtons = new Container(screen);
		modelButtons.setLayoutManager(new MigLayout(screen, "fill, gap 0, ins 0", "[][][]", "[]"));
		modelButtons.addChild(addButton);
		modelButtons.addChild(removeButton);
		modelButtons.addChild(saveButton);
		contentArea.addChild(modelButtons, "span 2, growx");

		// Pack and show
		setAvailable();
		clutterDefEditWindow.setIsResizable(true);
		screen.addElement(clutterDefEditWindow, null, true);
		clutterDefEditWindow.sizeToContent();
		clutterDefEditWindow.showWithEffect();

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
		if (queueUpdate && clutterDefEditWindow != null && clutterDefEditWindow.getState().equals(FancyWindow.State.MINIMIZED)) {
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
		clutterDefEditWindow.hideWindow();
	}

	private void resetClutterTable() {
		modelsTable.removeAllRows();
		if (splatImageName != null) {
			clutterDefEditWindow.setWindowTitle(String.format("Clutter (%s)", splatImageName));
			if (def == null) {
				ElementStyle.errorColor(screen, clutterDefEditWindow.getDragBar());
			} else {
				ElementStyle.normalColor(screen, clutterDefEditWindow.getDragBar());
				density.setSelectedValue(def.getDensity());
			}
		} else {
			clutterDefEditWindow.setWindowTitle("Clutter");
		}
	}

	private void setAvailable() {
		final boolean selEmpty = modelsTable.getSelectedRows().isEmpty();
		removeButton.setIsEnabled(!selEmpty);
		saveButton.setIsEnabled(def != null);
	}

	private void addRow(final String meshName, float density, boolean queue) {
		// Model
		final Table.TableCell cell1 = new Table.TableCell(screen, meshName, meshName);
		cell1.setPreferredDimensions(null);
		cell1.setTextVAlign(BitmapFont.VAlign.Bottom);
		cell1.setTextAlign(BitmapFont.Align.Center);

		final String propName = String.format("Prop-Clutter1#%s", Icelib.getBasename(meshName));
		final AbstractProp prop = parent.getPropFactory().getProp(propName);
		prop.getSpatial().rotate(0, -FastMath.HALF_PI, 0);
		prop.getSpatial().scale(0.25f);

		// Density
		final Table.TableCell cell2 = new Table.TableCell(screen, null, density);
		cell2.setHAlign(BitmapFont.Align.Center);

		Spinner<Float> itemDensity = new Spinner<Float>(screen, Orientation.HORIZONTAL, true) {
			@Override
			public void onChange(Float value) {
				def.getMeshScales().put(meshName, value);
			}
		};
		itemDensity.setSpinnerModel(new FloatRangeSpinnerModel(0, 10f, 0.1f, density));
		itemDensity.setDocking(null);
		itemDensity.setScaleEW(false);
		itemDensity.setScaleNS(false);
		cell2.addChild(itemDensity);

		if (queue) {
			app.run(new Runnable() {
				public void run() {
					doAddRow(cell1, cell2, propName, prop);
				}
			});
		} else {
			doAddRow(cell1, cell2, propName, prop);
		}
	}

	private void doAddRow(TableCell cell1, TableCell cell2, String propName, AbstractProp prop) {
		Node n = new Node();
		n.attachChild(prop.getSpatial());
		OSRViewPort vp = new OSRViewPort(screen, new Vector2f(100, 100), new Vector2f(100, 100), Vector4f.ZERO, null) {
			@Override
			public void controlHideHook() {
				try {
					super.controlHideHook();
				} catch (Exception e) {
					e.printStackTrace();
					;
					// Bug?
				}
			}
		};
		vp.setIgnoreMouse(true);
		vp.setOSRBridge(n, 100, 100);
		vp.setDocking(null);
		vp.setScaleEW(false);
		vp.setScaleNS(false);
		cell1.addChild(vp);

		final Table.TableRow row = new Table.TableRow(screen, modelsTable);
		row.addChild(cell1);
		row.addChild(cell2);
		row.setToolTipText(Icelib.getBasename(Icelib.getFilename(propName)));
		modelsTable.addRow(row);

		setAvailable();

		// Must do after added to scene
		AmbientLight al = new AmbientLight();
		al.setColor(ColorRGBA.White.mult(3));
		n.addLight(al);

	}
}
