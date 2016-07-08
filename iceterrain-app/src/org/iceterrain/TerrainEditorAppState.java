package org.iceterrain;

import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;
import org.icelib.DOSWriter;
import org.icelib.Icelib;
import org.icelib.PageLocation;
import org.icelib.UndoManager;
import org.icescene.Alarm;
import org.icescene.SceneConfig;
import org.icescene.SceneConstants;
import org.icescene.assets.Assets;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.environment.EnvironmentLight;
import org.icescene.io.ModifierKeysAppState;
import org.icescene.io.MouseManager;
import org.icescene.io.PNGSaver;
import org.icescene.props.EntityFactory;
import org.icescene.terrain.SaveableHeightMap;
import org.icescene.terrain.SaveableWideImageBasedHeightMap;
import org.iceskies.environment.EnvironmentManager;
import org.iceskies.environment.EnvironmentSwitcherAppState.EnvPriority;
import org.iceterrain.brushes.AbstractHeightBrush;
import org.iceterrain.brushes.TerrainFlattenBrush;
import org.iceterrain.brushes.TerrainHeightBrush;
import org.iceterrain.brushes.TerrainSmoothBrush;
import org.iceterrain.brushes.TerrainSplatBrush;
import org.iceui.HPosition;
import org.iceui.UIConstants;
import org.iceui.VPosition;
import org.iceui.controls.ElementStyle;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyPersistentWindow;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.IconTabControl;
import org.iceui.controls.SaveType;
import org.iceui.controls.TabPanelContent;
import org.iceui.controls.UIUtil;
import org.iceui.controls.XSeparator;
import org.iceui.effects.EffectHelper;

import com.jme3.app.state.AppStateManager;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapFont.Align;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.debug.Grid;
import com.jme3.scene.shape.Sphere;
import com.jme3.terrain.geomipmap.TerrainPatch;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.texture.Image;
import com.zero_separation.plugins.imagepainter.ImagePainter;

import icemoon.iceloader.ServerAssetManager;
import icetone.controls.buttons.Button;
import icetone.controls.buttons.ButtonAdapter;
import icetone.controls.buttons.CheckBox;
import icetone.controls.buttons.RadioButton;
import icetone.controls.buttons.RadioButtonGroup;
import icetone.controls.form.Form;
import icetone.controls.lists.ComboBox;
import icetone.controls.lists.FloatRangeSpinnerModel;
import icetone.controls.lists.IntegerRangeSpinnerModel;
import icetone.controls.lists.Spinner;
import icetone.controls.lists.Table;
import icetone.controls.text.Label;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.Element.Orientation;
import icetone.core.Element.ZPriority;
import icetone.core.ElementManager;
import icetone.core.layout.BorderLayout;
import icetone.core.layout.FlowLayout;
import icetone.core.layout.mig.MigLayout;
import icetone.core.utils.UIDUtil;
import icetone.effects.Effect;

public class TerrainEditorAppState extends TerrainAppState
		implements MouseManager.Listener, ModifierKeysAppState.Listener, ActionListener {

	private final static String MAPPING_UNDO = "Undo";
	private final static String MAPPING_REDO = "Redo";
	private final static int BASELINE_GRID_SIZE = 1920;
	private Spinner<Float> flattenElevation;
	private float oldRepeatDelay;
	private float oldRepeatInterval;
	private ModifierKeysAppState mods;
	private boolean reverseBrush;
	private TerrainSplatBrush splatBrush;
	// private TerrainHeightBrush heightBrush;
	private AbstractHeightBrush heightBrush;
	private AbstractHeightBrush flattenBrush;
	// private TerrainSmoothBrush smoothBrush;
	private UndoManager undoManager;
	private Alarm.AlarmTask undoRepeatTask;
	private Alarm.AlarmTask redoRepeatTask;
	private Container layer;
	private FancyButton undoButton;
	private FancyButton redoButton;
	private UndoManager.ListenerAdapter undoListener;
	private ButtonAdapter newTile;
	private ButtonAdapter deleteTile;
	private FancyButton saveEnv;
	private FancyButton resetEnv;
	private int lastMods;
	private IconTabControl tabs;
	private RadioButton raise;
	private RadioButton lower;
	private Spinner<Integer> flattenSize;
	private Spinner<Integer> splatSize;
	private Spinner<Float> splatRate;
	private Spinner<Float> amount;
	private Spinner<Integer> size;
	private Table heightBrushTexture;
	private Table splatBrushTexture;
	private CheckBox wireframe;
	private TerrainInstance terrainInstance;
	private Label totalMemory;
	private Label freeMemory;
	private Label maxMemory;
	private Label usedMemory;
	private Label undos;
	private Alarm.AlarmTask statsUpdateTask;

	public enum TerrainEditorMode {

		SELECT, PAINT, ERASE, FLATTEN, SMOOTH, SPLAT
	}

	private Geometry cursorGeometry;
	private Spatial cursorHandle;
	private Node cursorSpatial;
	private MouseManager.Mode oldMode;
	private FloatRangeSpinnerModel amountModel;
	private IntegerRangeSpinnerModel sizeModel;
	private IntegerRangeSpinnerModel flattenSizeModel;
	private FloatRangeSpinnerModel splatRateModel;
	private FloatRangeSpinnerModel flattenElevationModel;
	private IntegerRangeSpinnerModel splatSizeModel;
	private float terrainHeight;
	private RadioButtonGroup splatGroup;
	private int selectedTexture;
	final static Logger LOG = Logger.getLogger(TerrainEditorAppState.class.getName());
	private SplatControl texture0;
	private SplatControl texture1;
	private SplatControl texture2;
	private SplatControl texture3;
	private FancyPersistentWindow terrainEditWindow;
	private boolean adjusting;
	private Collection<String> imageResources;
	private ComboBox<String> liquidPlane;
	private Spinner<Float> elevation;
	private Spinner<Float> baseline;
	private TerrainEditorMode mode = TerrainEditorMode.SELECT;
	private Vector2f cursor = new Vector2f(0, 0);
	private FancyButton setElevation;
	private CheckBox snapToQuad;
	private ComboBox<String> tileEnvironment;
	private Geometry gridGeom;
	private int paints;
	private RadioButton smooth;
	private Node arrowSpatial;
	private ModeTab selectTab;
	private Spinner<Float> newElevation;

	public static File getDirectoryForTerrainTemplate(Assets assets, TerrainTemplateConfiguration terrainTemplate) {
		final String terrainDir = terrainTemplate.getAssetFolder();
		LOG.info(String.format("New terrain directory will be %s", terrainDir));
		File cfgDir = new File(assets.getExternalAssetsFolder(), terrainDir.replace('/', File.separatorChar));
		return cfgDir;
	}

	public static boolean isEditing(AppStateManager stateManager) {
		return stateManager.getState(TerrainEditorAppState.class) != null;
	}

	public static TerrainAppState toggle(AppStateManager stateManager) {
		TerrainEditorAppState editor = stateManager.getState(TerrainEditorAppState.class);
		if (editor == null) {
			// Try the standard read-only renderer
			LOG.info("Switching to terrain editor");
			TerrainAppState viewer = stateManager.getState(TerrainAppState.class);
			if (viewer == null) {
				throw new IllegalStateException("Must first attach a terrain appstate before toggling it.");
			} else {
				// We current have an viewer, make an editor
				stateManager.detach(viewer);
				final TerrainEditorAppState terrainEditorAppState = new TerrainEditorAppState(viewer.terrainLoader,
						viewer.getPreferences(), viewer.light, viewer.mappableNode, viewer.propFactory, viewer.worldNode,
						viewer.mouseManager);
				terrainEditorAppState.playerTile = viewer.playerTile;
				terrainEditorAppState.viewLocation = viewer.viewLocation;
				stateManager.attach(terrainEditorAppState);
				return terrainEditorAppState;
			}
		} else {
			// We current have an editor, make a viewer
			LOG.info("Switching to terrain viewer");
			stateManager.detach(editor);
			final TerrainAppState terrainAppState = new TerrainAppState(editor.terrainLoader, editor.prefs, editor.light,
					editor.mappableNode, editor.propFactory, editor.worldNode, editor.mouseManager);
			terrainAppState.playerTile = editor.playerTile;
			terrainAppState.viewLocation = editor.viewLocation;
			stateManager.attach(terrainAppState);
			return terrainAppState;
		}
	}

	public TerrainEditorAppState(TerrainLoader terrainLoader, Preferences prefs, EnvironmentLight light, Node mappableNode,
			EntityFactory propFactory, Node worldNode, MouseManager mouseManager) {
		super(terrainLoader, prefs, light, mappableNode, propFactory, worldNode, mouseManager);
		addPrefKeyPattern(TerrainConfig.TERRAIN_EDITOR + ".*");
	}

	@Override
	protected void beforeTerrainInitialize() {
		LOG.info("Initalizing terrain editing");

		amountModel = new FloatRangeSpinnerModel(0.1f, 50, 0.1f,
				prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT, TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT_DEFAULT));
		sizeModel = new IntegerRangeSpinnerModel(1, 32, 1,
				prefs.getInt(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE, TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE_DEFAULT));
		splatSizeModel = new IntegerRangeSpinnerModel(1, 32, 1,
				prefs.getInt(TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE, TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE_DEFAULT));
		splatRateModel = new FloatRangeSpinnerModel(0, 20, 0.1f,
				prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE, TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE_DEFAULT));
		flattenElevationModel = new FloatRangeSpinnerModel(-9999, 9999, 1f, 0f);
		flattenSizeModel = new IntegerRangeSpinnerModel(1, 32, 1, prefs.getInt(TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE,
				TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE_DEFAULT));

		terrainLoader.setReadOnly(false);
		recreateCursor();

		// Layer for some extra on screen buttons
		layer = new Container(screen);
		layer.setLayoutManager(new MigLayout(screen, "fill", "[][]push[][]", "[]push"));
		app.getLayers(ZPriority.NORMAL).addChild(layer);

		// Wireframe
		wireframe = new CheckBox(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				prefs.putBoolean(TerrainConfig.TERRAIN_WIREFRAME, toggled);
			}
		};
		wireframe
				.setIsCheckedNoCallback(prefs.getBoolean(TerrainConfig.TERRAIN_WIREFRAME, TerrainConfig.TERRAIN_WIREFRAME_DEFAULT));
		wireframe.setLabelText("Wireframe");
		layer.addChild(wireframe);

		// Snap to quad
		snapToQuad = new CheckBox(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				prefs.putBoolean(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD, toggled);
			}
		};
		snapToQuad.setIsCheckedNoCallback(
				prefs.getBoolean(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD, TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD_DEFAULT));
		snapToQuad.setLabelText("Snap To Quad");
		layer.addChild(snapToQuad);

		// Undo
		undoButton = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				maybeDoUndo();
			}

			@Override
			public void onButtonStillPressedInterval() {
				maybeDoUndo();
			}
		};
		undoButton.setInterval(SceneConstants.UNDO_REDO_REPEAT_INTERVAL);
		undoButton.setText("Undo");
		layer.addChild(undoButton);

		// Redo
		redoButton = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				maybeDoRedo();
			}

			@Override
			public void onButtonStillPressedInterval() {
				maybeDoRedo();
			}
		};
		redoButton.setInterval(SceneConstants.UNDO_REDO_REPEAT_INTERVAL);
		redoButton.setText("Redo");
		layer.addChild(redoButton);

		// Input
		app.getKeyMapManager().addMapping(MAPPING_UNDO);
		app.getKeyMapManager().addMapping(MAPPING_REDO);
		app.getKeyMapManager().addListener(this, MAPPING_UNDO, MAPPING_REDO);

		// try {
		// undoManager = new UndoManager(new DiskBackedHistoryStorage());
		// } catch (IOException ex) {
		// LOG.warning("Could not create disk based undo manager, falling back
		// to in-memory storage.");
		undoManager = new UndoManager();
		// }
		undoManager.addListener(undoListener = new UndoManager.ListenerAdapter() {
			@Override
			protected void change() {
				setAvailable();
				// operationEnded();
			}
		});

		mods = stateManager.getState(ModifierKeysAppState.class);
		mods.addListener(this);
		oldMode = mouseManager.getMode();
		oldRepeatDelay = mouseManager.getRepeatDelay();
		oldRepeatInterval = mouseManager.getRepeatInterval();
		mouseManager.setRepeatDelay(TerrainConstants.MOUSE_REPEAT_DELAY);
		mouseManager.setRepeatInterval(TerrainConstants.MOUSE_REPEAT_INTERVAL);
		mouseManager.setMode(MouseManager.Mode.NORMAL);
		imageResources = ((ServerAssetManager) app.getAssetManager()).getAssetNamesMatching(".*\\.png|.*\\.jpg|.*\\.jpeg|.*\\.gif");
		mouseManager.addListener(this);

		recreateCursor();
		terrainEditWindow();
		setSplatTabValues();

		rescheduleStatsUpdate();
	}

	protected void maybeDoUndo() {
		if (undoManager.isUndoAvailable()) {
			undoManager.undo();
		} else {
			info("Nothing more to undo.");
		}
	}

	protected void maybeDoRedo() {
		if (undoManager.isRedoAvailable()) {
			undoManager.redo();
		} else {
			info("Nothing more to redo.");
		}
	}

	@Override
	protected void onTerrainCleanup() {

		if (gridGeom != null) {
			gridGeom.removeFromParent();
		}

		statsUpdateTask.cancel();
		app.getLayers(ZPriority.NORMAL).removeChild(layer);

		undoManager.removeListener(undoListener);
		app.getKeyMapManager().removeListener(this);
		app.getKeyMapManager().deleteMapping(MAPPING_UNDO);
		app.getKeyMapManager().deleteMapping(MAPPING_REDO);

		mods.removeListener(this);
		mouseManager.setRepeatDelay(oldRepeatDelay);
		mouseManager.setRepeatInterval(oldRepeatInterval);
		mouseManager.removeListener(this);
		mouseManager.setMode(oldMode);
		new EffectHelper().effect(terrainEditWindow, Effect.EffectType.FadeOut, Effect.EffectEvent.Hide, UIConstants.UI_EFFECT_TIME)
				.setDestroyOnHide(true);

		terrainLoader.setReadOnly(true);
	}

	@Override
	public void playerTileChanged(PageLocation loc) {
		super.playerTileChanged(loc);
		recreateCursor();
		if (selectTab != null && loc != null)
			selectTab.setIsEnabled(loc.isValid());
		if (terrainEditWindow != null) {
			terrainEditWindow.setWindowTitle(String.format("Terrain - %d,%d", loc.x, loc.y));
			setSplatTabValues();
		}
		TerrainInstance page = getPage(loc);
		if (page != null) {
			TerrainTemplateConfiguration terrainTemplate = page.getTerrainTemplate();
			String environment = terrainTemplate.getEnvironment();
			tileEnvironment.setSelectedByValue(environment == null || StringUtils.isBlank(environment) ? "" : environment, false);
		}
	}

	public MouseManager.SelectResult isSelectable(MouseManager manager, Spatial spatial, MouseManager.Action action) {
		if (spatial.equals(cursorHandle)) {
			return MouseManager.SelectResult.YES;
		}
		return MouseManager.SelectResult.NO;
	}

	public void place(MouseManager manager, Vector3f location) {
	}

	public void hover(MouseManager manager, Spatial spatial, ModifierKeysAppState mods) {
	}

	public void click(MouseManager manager, Spatial spatial, ModifierKeysAppState mods, int startModsMask, Vector3f contactPoint,
			CollisionResults results, float tpf, boolean repeat) {
		// We might click on the cursor in paint / editmode, this should paint
		// the terrain underneath the cursor
		paints = 0;
		for (CollisionResult r : results) {
			Vector3f v = r.getContactPoint();
			if (r.getGeometry() instanceof TerrainPatch) {
				cursor.x = v.x;
				cursor.y = v.z;
				boolean moved = repositionCursor(true);
				if (mode.equals(TerrainEditorMode.FLATTEN) && !manager.isDraggingSpatial()) {
					setFlattenElevation();
					recreateFlattenBrush();
				} else {
					if (moved || !mode.equals(TerrainEditorMode.FLATTEN)) {
						paintAtCursor(tpf);
					}
				}
				break;
			}
		}
		operationEnded();
	}

	public void defaultSelect(MouseManager manager, ModifierKeysAppState mods, CollisionResults collision, float tpf) {
		for (CollisionResult r : collision) {
			Vector3f v = r.getContactPoint();
			if (r.getGeometry() instanceof TerrainPatch) {
				cursor.x = v.x;
				cursor.y = v.z;
				repositionCursor(true);
				if (flattenElevation != null) {
					setFlattenElevation();
				}
				if (mode.equals(TerrainEditorMode.FLATTEN)) {
					recreateFlattenBrush();
				} else {
					paintAtCursor(tpf);
				}
				break;
			}
		}
		screen.resetTabFocusElement();
	}

	public void dragEnd(MouseManager manager, Spatial spatial, ModifierKeysAppState mods, int startModsMask) {
		operationEnded();
	}

	public void dragStart(Vector3f click3d, MouseManager manager, Spatial spatial, ModifierKeysAppState mods, Vector3f direction) {
		paints = 0;
	}

	public void drag(MouseManager manager, Spatial spatial, ModifierKeysAppState mods, Vector3f click3d, Vector3f lastClick3d,
			float tpf, int startModsMask, CollisionResults results, Vector3f lookDir) {
		if (spatial.equals(cursorHandle)) {
			for (CollisionResult r : results) {
				Vector3f v = r.getContactPoint();
				if (r.getGeometry() instanceof TerrainPatch) {
					cursor.x = v.x;
					cursor.y = v.z;

					if (repositionCursor(true) || !mode.equals(TerrainEditorMode.FLATTEN)) {
						paintAtCursor(tpf);
						maybeSetFlattenElevation();
					}
					return;
				}
			}
		}
	}

	public void setMode(TerrainEditorMode mode) {
		switch (mode) {
		case PAINT:
			tabs.setSelectedTabIndex(1);
			raise.setIsToggled(true);
			break;
		case ERASE:
			tabs.setSelectedTabIndex(1);
			lower.setIsToggled(true);
			break;
		case FLATTEN:
			tabs.setSelectedTabIndex(2);
			break;
		case SMOOTH:
			tabs.setSelectedTabIndex(1);
			smooth.setIsToggled(true);
			break;
		case SELECT:
			tabs.setSelectedTabIndex(0);
			break;
		case SPLAT:
			tabs.setSelectedTabIndex(4);
			break;
		default:
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public void modifiersChange(int newMods) {

		if (!ModifierKeysAppState.isCtrl(lastMods) && ModifierKeysAppState.isCtrl(newMods)) {
			// Ctrl was pressed
			if (mode.equals(TerrainEditorMode.PAINT)) {
				reverseBrush = true;
				setMode(TerrainEditorMode.ERASE);
			} else if (mode.equals(TerrainEditorMode.ERASE)) {
				reverseBrush = true;
				setMode(TerrainEditorMode.PAINT);
			}
		} else if (ModifierKeysAppState.isCtrl(lastMods) && !ModifierKeysAppState.isCtrl(newMods)) {
			// Ctrl was released
			if (reverseBrush) {
				if (mode.equals(TerrainEditorMode.PAINT)) {
					setMode(TerrainEditorMode.ERASE);
				} else if (mode.equals(TerrainEditorMode.ERASE)) {
					setMode(TerrainEditorMode.PAINT);
				}
				reverseBrush = false;
			}
		}
		lastMods = newMods;

		// Disable the flycams rise / fall if we press Ctrl, as we want to use Z
		// as well
		app.getExtendedFlyByCamera().setAllowRiseAndLower(mods.isCtrl());

		reverseBrush = mods.isCtrl();
		recreateCursor();
		if (mode.equals(TerrainEditorMode.ERASE) || mode.equals(TerrainEditorMode.PAINT)) {
			recreateHeightBrush();
		}
	}

	@Override
	public List<Future<TerrainInstance>> reloadTerrain() {
		final List<Future<TerrainInstance>> futures = super.reloadTerrain();
		for (Future<TerrainInstance> f : futures) {
			try {
				f.get();
			} catch (Exception ex) {
				LOG.log(Level.SEVERE, "Exception waiting for terrain.", ex);
			}
		}
		setSplatTabValues();
		setAvailable();
		return futures;
	}

	public void onAction(String name, boolean isPressed, float tpf) {
		if (name.equals(MAPPING_UNDO)) {
			cancelUndoRepeat();
			if (isPressed && undoManager.isUndoAvailable() && mods.isCtrl()) {
				undoManager.undo();
				scheduleUndoRepeat(SceneConstants.KEYBOARD_REPEAT_DELAY);
			}
		} else if (name.equals(MAPPING_REDO)) {
			cancelRedoRepeat();
			if (isPressed && undoManager.isRedoAvailable() && mods.isCtrl()) {
				undoManager.redo();
				scheduleRedoRepeat(SceneConstants.KEYBOARD_REPEAT_DELAY);
			}
		}
	}

	protected void operationEnded() {
		for (TerrainInstance t : terrainLoader.getLoaded()) {
			if (t.isNeedsEdgeFix()) {
				terrainLoader.syncEdges(t);
				
//				t.copyQuadHeightmapToStoredHeightmap();
//				if (terrainLoader.syncEdges(t)) {
//					Icelib.dumpTrace();
//					LOG.info("Operation ended. There are edges to sync, refreshing from heightmap data");
//					t.copyStoredHeightmapToQuad();
//				}
			}
		}
	}

	@Override
	protected void handlePrefUpdateSceneThread(PreferenceChangeEvent evt) {
		super.handlePrefUpdateSceneThread(evt);
		if (evt.getKey().equals(TerrainConfig.TERRAIN_WIREFRAME)) {
			// This is put on the queue to work around a tonegodgui bug
			app.enqueue(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					wireframe.setIsCheckedNoCallback(
							prefs.getBoolean(TerrainConfig.TERRAIN_WIREFRAME, TerrainConfig.TERRAIN_WIREFRAME_DEFAULT));
					return null;
				}
			});
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD)) {
			// This is put on the queue to work around a tonegodgui bug
			app.enqueue(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					snapToQuad.setIsCheckedNoCallback(prefs.getBoolean(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD,
							TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD_DEFAULT));
					return null;
				}
			});
			repositionCursor(false);
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE)) {
			splatSize.setSelectedValue(prefs.getInt(TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE,
					TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE_DEFAULT));
			destroyCursor();
			recreateCursor();
			recreateSplatBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE)) {
			flattenSize.setSelectedValue(prefs.getInt(TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE,
					TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE_DEFAULT));
			destroyCursor();
			recreateCursor();
			recreateFlattenBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE)) {
			splatRate.setSelectedValue(
					prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE, TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE_DEFAULT));
			recreateSplatBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE)) {
			size.setSelectedValue(prefs.getInt(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE,
					TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE_DEFAULT));
			destroyCursor();
			recreateCursor();
			recreateHeightBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT)) {
			amount.setSelectedValue(
					prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT, TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT_DEFAULT));
			recreateHeightBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_BASELINE)
				|| evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE)) {
			checkBaselineGrid();
			baseline.getSpinnerModel().setValueFromString(String
					.valueOf(prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_BASELINE, TerrainConfig.TERRAIN_EDITOR_BASELINE_DEFAULT)));
		}

	}

	protected void onElevationChange() {
	}

	@Override
	protected void doTileLoaded(TerrainInstance instance) {
		super.doTileLoaded(instance);
		setSplatTabValues();
		repositionCursor(true);
	}

	private void checkBaselineGrid() {
		boolean wantGrid = prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
				TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT);
		boolean haveGrid = gridGeom != null;
		if (wantGrid != haveGrid) {
			if (wantGrid) {
				Grid grid = new Grid(BASELINE_GRID_SIZE, BASELINE_GRID_SIZE, 4);
				gridGeom = new Geometry("Grid", grid);
				Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
				mat.setColor("Color", ColorRGBA.Gray);
				gridGeom.setLocalTranslation(-BASELINE_GRID_SIZE / 2, 0, -BASELINE_GRID_SIZE / 2);
				gridGeom.setMaterial(mat);
				rootNode.attachChild(gridGeom);
			} else {
				gridGeom.removeFromParent();
				gridGeom = null;
			}
		}
		recreateFlattenBrush();
		recreateHeightBrush();
		recreateSplatBrush();
		repositionBaseGrid();

	}

	private void updateCursorBasedConfiguration() {
		if (newElevation != null)
			newElevation.setSelectedValue(Float.isNaN(terrainHeight) ? 0 : terrainHeight);
	}

	private void repositionBaseGrid() {
		if (gridGeom != null) {
			gridGeom.setLocalTranslation(cursor.x - (BASELINE_GRID_SIZE / 2),
					prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_BASELINE, TerrainConfig.TERRAIN_EDITOR_BASELINE_DEFAULT) + 0.2f,
					cursor.y - (BASELINE_GRID_SIZE / 2));
		}

	}

	private void cancelUndoRepeat() {
		if (undoRepeatTask != null) {
			undoRepeatTask.cancel();
		}
	}

	private Table createBrushSelector(final Runnable onChange) {
		Table brushSelector = new Table(screen) {
			@Override
			public void onChange() {
				onChange.run();
			}
		};
		brushSelector.setSelectionMode(Table.SelectionMode.ROW);
		brushSelector.addColumn("Image");
		brushSelector.addColumn("Name");
		brushSelector.setHeadersVisible(false);
		for (String a : ((ServerAssetManager) app.getAssetManager()).getAssetNamesMatching("Textures/Brushes/.*\\.png")) {
			Table.TableRow r = new Table.TableRow(screen, brushSelector, UIDUtil.getUID(), a);
			Table.TableCell c1 = new Table.TableCell(screen, a);
			c1.setPreferredDimensions(new Vector2f(32, 32));
			r.addChild(c1);
			Element img = new Element(screen, UIDUtil.getUID(), new Vector2f(32, 32), Vector4f.ZERO, a);
			img.setIgnoreMouse(true);
			c1.addChild(img);
			Table.TableCell c2 = new Table.TableCell(screen, Icelib.getBaseFilename(a), a);
			c2.setPreferredDimensions(new Vector2f(32, 40));
			r.addChild(c2);
			brushSelector.addRow(r);
		}
		if (brushSelector.getRowCount() > 0) {
			brushSelector.setSelectedRowIndex(0);
		}
		brushSelector.setColumnResizeMode(Table.ColumnResizeMode.AUTO_ALL);
		return brushSelector;
	}

	private void cancelRedoRepeat() {
		if (redoRepeatTask != null) {
			redoRepeatTask.cancel();
		}
	}

	private void scheduleUndoRepeat(float delay) {
		undoRepeatTask = app.getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				if (undoManager.isUndoAvailable()) {
					undoManager.undo();
					scheduleUndoRepeat(SceneConstants.KEYBOARD_REPEAT_INTERVAL);
				}
				return null;
			}
		}, delay);
	}

	private void scheduleRedoRepeat(float delay) {
		redoRepeatTask = app.getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				if (undoManager.isRedoAvailable()) {
					undoManager.redo();
					scheduleRedoRepeat(SceneConstants.KEYBOARD_REPEAT_INTERVAL);
				}
				return null;
			}
		}, delay);
	}

	private void paintAtCursor(float tpf) {
		if (mode.equals(TerrainEditorMode.SPLAT)) {
			splatBrush.paint(cursor, tpf, paints++);
			maybeReloadClutter();
		} else if (mode.equals(TerrainEditorMode.FLATTEN)) {
			flattenBrush.paint(cursor, tpf, paints++);
			maybeReloadClutter();
		} else if (!mode.equals(TerrainEditorMode.SELECT)) {
			heightBrush.paint(cursor, tpf, paints++);
			maybeReloadClutter();
		}
	}

	private void maybeReloadClutter() {
		if (clutter != null) {
			clutter.timedReload();
		}
	}

	private boolean repositionCursor(boolean y) {
		// Snap cursor (a grid) to terrain quads when flattening
		float hs = 0;
		if (snapToQuad != null && snapToQuad.getIsChecked()) {
			TerrainTemplateConfiguration cfg = getConfigurationForLocation();
			if (cfg != null) {
				if (mode.equals(TerrainEditorMode.SPLAT)) {
					float cs = cfg.getCoverageScale();
					cursor.x = (int) (cursor.x / cs) * cs;
					cursor.y = (int) (cursor.y / cs) * cs;

				} else {
					float ps = cfg.getPageScale();
					hs = ps / 2f;
					// System.err.println("REMOVEME PS: " + ps);
					cursor.x = ((int) ((cursor.x - hs) / ps) * ps) + hs;
					cursor.y = ((int) ((cursor.y - hs) / ps) * ps) + hs;
				}
			}
		}

		terrainInstance = terrainLoader.getPageAtWorldPosition(cursor);
		if (y) {
			terrainHeight = terrainInstance == null ? Float.MIN_VALUE : terrainInstance.getHeightAtWorldPosition(cursor);
			if (terrainHeight == Float.MIN_VALUE) {
				terrainHeight = 0;
			}
		}
		if (terrainInstance != null) {

			Quaternion q = new Quaternion();
			Vector3f slopeAtWorldPosition = terrainInstance.getSlopeAtWorldPosition(cursor);
			if (slopeAtWorldPosition != null) {
				q.fromAngleNormalAxis(90, slopeAtWorldPosition);
				arrowSpatial.setLocalRotation(q);
			}
		}
		Vector3f current = cursorSpatial.getLocalTranslation().clone();
		if (hs > 0) {
			cursorSpatial.setLocalTranslation(cursor.x + hs, terrainHeight, cursor.y + hs);
		} else {
			cursorSpatial.setLocalTranslation(cursor.x, terrainHeight, cursor.y);
		}
		repositionBaseGrid();
		updateCursorBasedConfiguration();
		return !cursorSpatial.getLocalTranslation().equals(current);
	}

	private void recreateCursor() {
		int size;
		final float brushCursorFactor = TerrainConstants.EDITOR_BRUSH_SCALE;
		if (isValidForEditing() && cursorSpatial == null) {
			LOG.info(String.format("Creating cursor for mode %s", mode));
			Mesh cursorMesh = null;

			// Choose size from appropriate model
			switch (mode) {
			case FLATTEN:
				size = flattenSizeModel.getCurrentValue();
				break;
			case SMOOTH:
			case PAINT:
			case ERASE:
				size = sizeModel.getCurrentValue();
				break;
			case SPLAT:
				size = splatSizeModel.getCurrentValue();
				break;
			default:
				size = 1;
				break;
			}

			// Create cursor shape
			switch (mode) {
			case PAINT:
			case FLATTEN:
			case SMOOTH:
			case ERASE:
			case SPLAT:
				cursorMesh = new Grid(size + 1, size + 1, 1);
				break;
			default:
				cursorMesh = new Sphere(16, 16, 1f);
				break;
			}

			// The geometry of the actual cursor
			cursorGeometry = new Geometry("Box", cursorMesh);
			cursorHandle = cursorGeometry;
			Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
			mat.setColor("Color", ColorRGBA.Orange);
			mat.getAdditionalRenderState().setWireframe(true);
			cursorGeometry.setMaterial(mat);

			// Wrap it in a node so we can offset the cursor if needed
			cursorSpatial = new Node();
			cursorSpatial.attachChild(cursorGeometry);
			switch (mode) {
			case PAINT:
			case ERASE:
			case SMOOTH:
			case SPLAT:
			case FLATTEN:
				// Add a sphere as the 'handle' for something to drag
				Mesh handleMesh = new Sphere(16, 16, 0.5f);
				cursorHandle = new Geometry("Handle", handleMesh);
				cursorHandle.scale(1, 16, 1);
				cursorSpatial.attachChild(cursorHandle);
				cursorHandle.setMaterial(mat);

				TerrainTemplateConfiguration cfg = getConfigurationForLocation();
				if (mode.equals(TerrainEditorMode.SPLAT)) {
					cursorSpatial.setLocalScale(cfg.getCoverageScale(), 1, cfg.getCoverageScale());
				} else {
					cursorSpatial.setLocalScale(cfg.getPageScale(), 1, cfg.getPageScale());
				}
				cursorGeometry.move((float) size / -2f, 1, (float) size / -2f);
				break;
			default:
				break;
			}

			// Arrows
			arrowSpatial = new Node();
			attachCoordinateAxes(arrowSpatial, Vector3f.ZERO);
			cursorSpatial.attachChild(arrowSpatial);

			// Attach to terrain
			terrainGroupNode.attachChild(cursorSpatial);
		} else if (!isValidForEditing() && cursorSpatial != null) {
			LOG.info("Not valid for editing, removing cursor");
			destroyCursor();
		}
		if (cursorSpatial != null) {
			repositionCursor(true);
			switch (mode) {
			case SMOOTH:
				cursorGeometry.getMaterial().setColor("Color", ColorRGBA.Magenta);
				// cursorGeometry.setLocalScale(brushCursorFactor *
				// flattenSizeAmount);
				break;
			case FLATTEN:
				cursorGeometry.getMaterial().setColor("Color", ColorRGBA.Blue);
				// cursorGeometry.setLocalScale(brushCursorFactor *
				// flattenSizeAmount);
				break;
			case PAINT:
				cursorGeometry.getMaterial().setColor("Color", ColorRGBA.Orange);
				// cursorSpatial.setLocalScale(brushCursorFactor * (float)
				// sizeModel.getCurrentValue());
				break;
			case ERASE:
				cursorGeometry.getMaterial().setColor("Color", ColorRGBA.Red);
				// cursorSpatial.setLocalScale(brushCursorFactor * (float)
				// sizeModel.getCurrentValue());
				break;
			case SPLAT:
				cursorGeometry.getMaterial().setColor("Color", ColorRGBA.Yellow);
				// cursorSpatial.setLocalScale(brushCursorFactor * (float)
				// splatSizeModel.getCurrentValue());
				break;
			default:
				cursorGeometry.getMaterial().setColor("Color", ColorRGBA.White);
				cursorSpatial.setLocalScale(brushCursorFactor * (float) sizeModel.getCurrentValue());
				break;
			}
		}
	}

	private void attachCoordinateAxes(Node spatial, Vector3f pos) {
		Arrow arrow = new Arrow(Vector3f.UNIT_X);
		arrow.setLineWidth(4); // make arrow thicker
		putShape(spatial, arrow, ColorRGBA.Red).setLocalTranslation(pos);

		arrow = new Arrow(Vector3f.UNIT_Y);
		arrow.setLineWidth(4); // make arrow thicker
		putShape(spatial, arrow, ColorRGBA.Green).setLocalTranslation(pos);

		arrow = new Arrow(Vector3f.UNIT_Z);
		arrow.setLineWidth(4); // make arrow thicker
		putShape(spatial, arrow, ColorRGBA.Blue).setLocalTranslation(pos);
	}

	private Geometry putShape(Node spatial, Mesh shape, ColorRGBA color) {
		Geometry g = new Geometry("coordinate axis", shape);
		Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mat.getAdditionalRenderState().setWireframe(true);
		mat.setColor("Color", color);
		g.setMaterial(mat);
		spatial.attachChild(g);
		return g;
	}

	private void destroyCursor() {
		if(cursorSpatial != null)
			cursorSpatial.removeFromParent();
		cursorSpatial = null;
	}

	private void setSplatTabValues() {
		adjusting = true;
		try {
			TerrainInstance page = playerTile == null ? null : getPage(playerTile);

			if (page != null) {
				final TerrainTemplateConfiguration configuration = page.getTerrainTemplate();
				final TerrainTemplateConfiguration.LiquidPlaneConfiguration config = configuration.getLiquidPlaneConfiguration();
				if (config != null && StringUtils.isNotBlank(config.getMaterial())) {
					final String liquidName = config.getMaterial();
					LOG.info(String.format("Selecting liquid %s", liquidName));
					liquidPlane.setSelectedByValue(liquidName, false);
					elevation.setSelectedValue(config.getElevation());
					elevation.setIsEnabled(true);
				} else {
					noLiquid();
				}
			} else {
				noLiquid();
			}

			if (texture0 != null) {
				if (page != null) {
					final TerrainTemplateConfiguration configuration = page.getTerrainTemplate();
					texture0.setIsEnabled(true);
					texture0.setValue(SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting0());
					texture1.setIsEnabled(true);
					texture1.setValue(SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting1());
					texture2.setIsEnabled(true);
					texture2.setValue(SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting2());
					texture3.setIsEnabled(true);
					texture3.setValue(SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting3());
				} else {
					texture0.setIsEnabled(false);
					texture1.setIsEnabled(false);
					texture2.setIsEnabled(false);
					texture3.setIsEnabled(false);
				}
			}

		} finally {
			adjusting = false;
			setAvailable();
		}

	}

	private void rescheduleStatsUpdate() {
		statsUpdateTask = app.getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				setMemoryValues();
				rescheduleStatsUpdate();
				return null;
			}
		}, 1);
	}

	private void closeTerrain() {
		terrainLoader.unloadAll();
		terrainLoader.setTerrainTemplate(null);
		TerrainEditorAppState.toggle(stateManager);
	}

	private void noLiquid() {
		liquidPlane.setSelectedByValue("None", false);
		elevation.setSelectedValue(0f);
		elevation.setIsEnabled(false);
	}

	private void terrainEditWindow() {
		adjusting = true;

		terrainEditWindow = new FancyPersistentWindow(screen, SceneConfig.TERRAIN,
				screen.getStyle("Common").getInt("defaultWindowOffset"), VPosition.MIDDLE, HPosition.RIGHT, new Vector2f(300, 560),
				FancyWindow.Size.SMALL, true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				closeTerrain();
			}

			@Override
			protected boolean canClose() {
				if (terrainLoader.isNeedsSave()) {
					final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
						@Override
						public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
							hideWindow();
						}

						@Override
						public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
							hideWindow();
							closeTerrain();
						}
					};
					dialog.setDestroyOnHide(true);
					dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
					dialog.setWindowTitle("Confirm Close Template");
					dialog.setButtonOkText("Close");
					dialog.setMsg("You have unsaved edits! Are you sure you wish to close this template?");
					dialog.setIsResizable(false);
					dialog.setIsMovable(false);
					dialog.sizeToContent();
					UIUtil.center(screen, dialog);
					screen.addElement(dialog, null, true);
					dialog.showAsModal(true);
					return false;
				}
				return true;
			}
		};
		terrainEditWindow.setMinimizable(true);
		terrainEditWindow.setWindowTitle("Terrain");
		final Element contentArea = terrainEditWindow.getContentArea();
		contentArea.setLayoutManager(new BorderLayout(4, 4));

		tabs = new IconTabControl(screen);
		tabs.addTabWithIcon("Select Mode", "Interface/Styles/Gold/Common/Icons/select.png");
		tabs.addTabChild(0, selectTab());
		tabs.addTabWithIcon("Raise or lower terrain", "Interface/Styles/Gold/Common/Icons/brush.png");
		tabs.addTabChild(1, paintTab());
		tabs.addTabWithIcon("Flatten terrain", "Interface/Styles/Gold/Common/Icons/roller.png");
		tabs.addTabChild(2, flattenTab());
		tabs.addTabWithIcon("Paint terrain textures", "Interface/Styles/Gold/Common/Icons/splat.png");
		tabs.addTabChild(3, splatTab());
		tabs.addTabWithIcon("Adjust liquid plane", "Interface/Styles/Gold/Common/Icons/wave.png");
		tabs.addTabChild(4, liquidTab());

		contentArea.addChild(tabs);

		// Buttons
		saveEnv = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				save();
			}
		};
		saveEnv.setText("Save");

		resetEnv = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				if (!adjusting && playerTile != null) {
					TerrainInstance page = getPage(playerTile);
					page.getTerrainTemplate().reset();
					reloadTerrain();
				}
			}
		};
		resetEnv.setText("Reset");

		Element bottom = new Element(screen);

		bottom.setLayoutManager(new FlowLayout(8, BitmapFont.Align.Center));
		bottom.addChild(saveEnv);
		bottom.addChild(resetEnv);
		contentArea.addChild(bottom, BorderLayout.Border.SOUTH);

		screen.addElement(terrainEditWindow);
		terrainEditWindow.showWindow();
		// terrainEditWindow.setIsResizable(false);

		recreateFlattenBrush();
		recreateHeightBrush();
		recreateSplatBrush();
		checkBaselineGrid();

		adjusting = false;

		setAvailable();
	}

	private void save() {
		if (!adjusting && playerTile != null) {
			int saved = 0;
			for (TerrainInstance page : terrainLoader.getLoaded()) {
				LOG.info(String.format("Maybe save %s", page.getPage()));
				if (page.isNeedsSave()) {
					LOG.info(String.format("Saving %s as there are changes", page.getPage()));
					try {
						saveTemplate(page);
						if (page.getHeightmap() instanceof SaveableHeightMap) {
							saveHeightmap(page);
						}
						writeCoverage(page);
						info(String.format("Saved terrain %d,%d", page.getPage().x, page.getPage().y));
						page.setNeedsSave(false);
						saved++;
					} catch (Exception e) {
						error(String.format("Faile to save terrain %d,%d", page.getPage().x, page.getPage().y), e);
						LOG.log(Level.SEVERE, "Failed to save terrain.", e);
					}
				} else {
					LOG.info(String.format("Don't need to save %s as there are no changes", page.getPage()));
				}
			}
			if (saved == 0) {
				error("Nothing to save!");
			}
		}
	}

	private void writeCoverage(TerrainInstance page) throws FileNotFoundException, IOException {

		String coverageName = format(page.getTerrainTemplate().getTextureCoverageFormat(), page.getPage().x, page.getPage().y);
		String coveragePath = String.format("%s/%s", page.getTerrainTemplate().getAssetFolder(), coverageName);

		Image img = page.getCoverage();
		File file = TerrainEditorAppState.this.app.getAssets().getExternalAssetFile(coveragePath);
		LOG.info(String.format("Writing coverage of %s to %s", page, file));
		FileOutputStream fos = new FileOutputStream(file);
		try {
			PNGSaver.save(fos, false, true, img);
		} finally {
			fos.close();
		}
	}

	private void saveHeightmap(TerrainInstance page) throws IOException, IllegalArgumentException, FileNotFoundException {
		SaveableHeightMap hm = (SaveableHeightMap) page.getHeightmap();
		page.copyQuadHeightmapToStoredHeightmap();
		Image img = hm.getColorImage();

		// TODO depth not working?
		int depth;
		switch (img.getFormat()) {
		case Luminance16:
			depth = 16;
			break;
		default:
			throw new IllegalArgumentException("Unknown depth for format " + img.getFormat() + ".");
		}

		String heightmapName = String.format(page.getTerrainTemplate().getHeightmapImageFormat(), page.getPage().x,
				page.getPage().y);
		String heightmapPath = String.format("%s/%s", page.getTerrainTemplate().getAssetFolder(), heightmapName);
		File heightmapFile = app.getAssets().getExternalAssetFile(heightmapPath);
		FileOutputStream out = new FileOutputStream(heightmapFile);
		try {
			LOG.info(String.format("Saving greyscale image based heightmap of %d x %d to %s (%d bpp %s)", img.getWidth(),
					img.getHeight(), heightmapFile, depth, img.getFormat()));
			hm.save(out, false, false);
			// saveAs16PNG(img, out);
		} finally {
			out.close();
		}
	}

	private void saveTemplate(TerrainInstance page) throws FileNotFoundException, IOException {
		String fileName = format(page.getTerrainTemplate().getPerPageConfig(), page.getPage().x, page.getPage().y);
		File file = TerrainEditorAppState.this.app.getAssets()
				.getExternalAssetFile(String.format("%s/%s", page.getTerrainTemplate().getAssetFolder(), fileName));
		LOG.info(String.format("Writing %s to %s", page, file));
		FileOutputStream fos = new FileOutputStream(file);
		try {
			page.getTerrainTemplate().write(fos, true);
		} finally {
			fos.close();
		}
	}

	@SuppressWarnings("serial")
	class SetSplatCommand implements UndoManager.UndoableCommand {

		private final int splat;
		private final String newImage;
		private String current;
		private final TerrainInstance instance;
		private final String dir;

		SetSplatCommand(TerrainInstance instance, String dir, String newImage, int splat) {
			this.instance = instance;
			this.dir = dir;
			this.splat = splat;
			this.newImage = newImage;
		}

		public void undoCommand() {
			TerrainTemplateConfiguration config = instance.getTerrainTemplate();
			switch (splat) {
			case 0:
				config.setTextureSplatting0(current);
				texture0.setValue(dir + "/" + current);
				break;
			case 1:
				config.setTextureSplatting1(current);
				texture1.setValue(dir + "/" + current);
				break;
			case 2:
				config.setTextureSplatting2(current);
				texture2.setValue(dir + "/" + current);
				break;
			case 3:
				config.setTextureSplatting3(current);
				texture3.setValue(dir + "/" + current);
				break;
			}
			terrainLoader.reconfigureTile(instance);
			instance.setNeedsSave(true);
		}

		public void doCommand() {
			TerrainTemplateConfiguration config = instance.getTerrainTemplate();
			switch (splat) {
			case 0:
				current = config.getTextureSplatting0();
				config.setTextureSplatting0(newImage);
				texture0.setValue(dir + "/" + newImage);
				break;
			case 1:
				current = config.getTextureSplatting1();
				config.setTextureSplatting1(newImage);
				texture1.setValue(dir + "/" + newImage);
				break;
			case 2:
				current = config.getTextureSplatting2();
				config.setTextureSplatting2(newImage);
				texture2.setValue(dir + "/" + newImage);
				break;
			case 3:
				current = config.getTextureSplatting3();
				config.setTextureSplatting3(newImage);
				texture3.setValue(dir + "/" + newImage);
				break;
			}
			terrainLoader.reconfigureTile(instance);
			instance.setNeedsSave(true);
		}
	}

	private TabPanelContent splatTab() {
		TabPanelContent toolOptions = new ModeTab(screen, TerrainEditorMode.SPLAT);
		toolOptions.setLayoutManager(new MigLayout(screen, "wrap 3, fill", "[16::][24::][fill, grow]",
				"[shrink 0][shrink 0][shrink 0][shrink 0][shrink 0][shrink 0][shrink 0][grow][shrink 0][shrink 0]"));

		Form f = new Form(screen);
		splatGroup = new RadioButtonGroup(screen) {
			@Override
			public void onSelect(int index, Button value) {
				if (!adjusting) {
					selectedTexture = index;
					recreateSplatBrush();
					recreateCursor();
				}
			}
		};

		// Splats
		toolOptions.addChild(ElementStyle.medium(screen, new Label("Layer", screen), true, false), "span 3");

		// Texture 0
		RadioButton t0 = new RadioButton(screen);
		splatGroup.addButton(t0);
		texture0 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 0));
			}
		};
		toolOptions.addChild(ElementStyle.normal(screen, new Label("A", screen), true, false));
		toolOptions.addChild(f.addFormElement(t0));
		toolOptions.addChild(texture0, "growx");
		f.addFormElement(texture0);

		// Texture 1
		RadioButton t1 = new RadioButton(screen);
		splatGroup.addButton(t1);
		texture1 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 1));
			}
		};
		toolOptions.addChild(ElementStyle.normal(screen, new Label("R", screen), true, false));
		toolOptions.addChild(t1);
		toolOptions.addChild(texture1, "growx");
		f.addFormElement(texture1);

		// Texture 2
		RadioButton t2 = new RadioButton(screen);
		splatGroup.addButton(t2);
		texture2 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 2));
			}
		};
		f.addFormElement(texture2);
		toolOptions.addChild(ElementStyle.normal(screen, new Label("G", screen), true, false));
		toolOptions.addChild(t2);
		toolOptions.addChild(texture2, "growx");

		// Texture 3
		RadioButton t3 = new RadioButton(screen);
		splatGroup.addButton(t3);
		texture3 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 3));
			}
		};
		f.addFormElement(texture3);
		toolOptions.addChild(ElementStyle.normal(screen, new Label("B", screen), true, false));
		toolOptions.addChild(t3);
		toolOptions.addChild(texture3, "growx");

		// Actions
		Container c = new Container(new FlowLayout(4, Align.Center));
		c.addChild(new ButtonAdapter(screen, "Copy") {

			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw, true);
				pw.println(texture0.getValue());
				pw.println(texture1.getValue());
				pw.println(texture2.getValue());
				pw.println(texture3.getValue());
				pw.close();
				screen.setClipboardText(sw.toString());
				info("Splat images copied to clipboard");
			}

		});
		c.addChild(new ButtonAdapter(screen, "Paste") {

			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				BufferedReader br = new BufferedReader(new StringReader(screen.getClipboardText()));
				try {
					String line = br.readLine();
					if (line != null) {
						texture0.setValueWithCallback(line);
						line = br.readLine();
						if (line != null) {
							texture1.setValueWithCallback(line);
							line = br.readLine();
							if (line != null) {
								texture2.setValueWithCallback(line);
								line = br.readLine();
								if (line != null) {
									texture3.setValueWithCallback(line);
								}
							}
						}
						info("Splat images pasted from clipboard");
					}
				} catch (IOException ioe) {
					// Wont' happen?
				}
			}

		});
		toolOptions.addChild(c, "span 3");

		// Set inital selection
		splatGroup.setSelected(selectedTexture);

		// Brush
		toolOptions.addChild(ElementStyle.medium(screen, new Label("Brush", screen), true, false), "span 3");

		toolOptions.addChild(splatBrushTexture = createBrushSelector(new Runnable() {
			public void run() {
				if (splatBrushTexture != null) {
					recreateSplatBrush();
				}
			}
		}), "growx, growy, span 3");

		// Other options
		Container other = new Container(screen);
		other.setLayoutManager(new MigLayout(screen, "wrap 2", "[][fill, grow]", "[][][]"));

		// Brush Size
		other.addChild(new Label("Size:", screen));
		splatSize = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false) {
			@Override
			public void onChange(Integer value) {
				prefs.putInt(TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE, value);
			}
		};
		splatSize.setInterval(10f);
		splatSize.setSpinnerModel(splatSizeModel);
		f.addFormElement(splatSize);
		other.addChild(splatSize);

		// Brush strength
		other.addChild(new Label("Rate:", screen));
		splatRate = new Spinner<Float>(screen, Orientation.HORIZONTAL, false) {
			@Override
			public void onChange(Float value) {
				prefs.putFloat(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE, value);
			}
		};
		splatRate.setInterval(25);
		splatRate.setSpinnerModel(splatRateModel);
		f.addFormElement(splatRate);
		other.addChild(splatRate);

		//
		toolOptions.addChild(other, "span 3, growx");

		recreateHeightBrush();

		recreateSplatBrush();
		return toolOptions;
	}

	private TabPanelContent paintTab() {
		final RadioButtonGroup paintGroup = new RadioButtonGroup(screen) {
			@Override
			public void onSelect(int index, Button value) {
				switch (index) {
				case 0:
					mode = TerrainEditorMode.PAINT;
					break;
				case 1:
					mode = TerrainEditorMode.ERASE;
					break;
				case 2:
					mode = TerrainEditorMode.SMOOTH;
					break;
				}
				recreateHeightBrush();
				recreateCursor();
			}
		};
		TabPanelContent toolOptions = new ModeTab(screen, TerrainEditorMode.PAINT) {
			@Override
			public void childShow() {
				super.childShow();
				paintGroup.setSelected(mode.equals(TerrainEditorMode.PAINT) ? 0 : 1);
			}
		};
		toolOptions.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[100:100:][fill, grow]", "[][][][][][fill,grow]"));

		// Brush Size
		toolOptions.addChild(new Label("Brush Size:", screen));
		size = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false) {
			@Override
			public void onChange(Integer value) {
				prefs.putInt(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE, value);
			}
		};
		size.setInterval(10f);
		size.setSpinnerModel(sizeModel);
		toolOptions.addChild(size);

		// Brush strength
		toolOptions.addChild(new Label("Amount:", screen));
		amount = new Spinner<Float>(screen, Orientation.HORIZONTAL, false) {
			@Override
			public void onChange(Float value) {
				prefs.putFloat(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT, value);
			}
		};
		amount.setInterval(100);
		amount.setSpinnerModel(amountModel);
		toolOptions.addChild(amount);

		raise = new RadioButton(screen);
		raise.setLabelText("Raise");
		paintGroup.addButton(raise);
		toolOptions.addChild(raise, "gapleft 32, growx, span 2");
		lower = new RadioButton(screen);
		lower.setLabelText("Lower");
		paintGroup.addButton(lower);
		toolOptions.addChild(lower, "gapleft 32, growx, span 2");
		smooth = new RadioButton(screen);
		smooth.setLabelText("Smooth");
		paintGroup.addButton(smooth);
		toolOptions.addChild(smooth, "gapleft 32, growx, span 2");

		toolOptions.addChild(heightBrushTexture = createBrushSelector(new Runnable() {
			public void run() {
				if (heightBrushTexture != null) {
					recreateHeightBrush();
				}
			}
		}), "growx, growy, span 2");

		recreateHeightBrush();

		return toolOptions;
	}

	private TabPanelContent selectTab() {
		selectTab = new ModeTab(screen, TerrainEditorMode.SELECT);
		selectTab.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[shrink 0][fill]", "[][][][]push[]"));

		Container a = new Container(screen);
		a.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[][]", "[]"));
		a.addChild(new Label("Tile environment:", screen));
		tileEnvironment = new ComboBox<String>(screen) {

			@Override
			public void onChange(int selectedIndex, String value) {
				if (adjusting) {
					return;
				}
				TerrainTemplateConfiguration cfg = getConfigurationForLocation();
				PageLocation page = cfg.getPage();
				File cfgDir = TerrainEditorAppState.getDirectoryForTerrainTemplate(getApp().getAssets(), cfg);
				File file = new File(cfgDir, String.format("%s_x%dy%d.nut", cfg.getBaseTemplateName(), page.x, page.y));
				try {
					if (value.equals("")) {
						cfg.setEnvironment(null);
						file.delete();
						info(String.format("Set terrain tile %d,%d to default environment for terrain.", page.x, page.y));
						setEnvironment(null, EnvPriority.TILE);
					} else {
						cfg.setEnvironment(value);
						DOSWriter dw = new DOSWriter(new FileOutputStream(file));
						try {
							dw.println(String.format("TerrainTemplate.setEnvironment(\"%s\");", value));
						} finally {
							dw.close();
						}
						info(String.format("Set terrain tile %d,%d to environment '%s'.", page.x, page.y, key));
						setEnvironment(value, EnvPriority.TILE);
					}
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to set tile environment.", e);
				}
			}
		};

		tileEnvironment.addListItem("Default for terrain", "");
		for (String key : EnvironmentManager.get(assetManager).getEnvironments()) {
			tileEnvironment.addListItem(key, key);
		}
		a.addChild(tileEnvironment);

		a.addChild(new Label("Base:", screen));

		baseline = new Spinner<Float>(screen, Orientation.HORIZONTAL, true) {
			@Override
			public void onChange(Float value) {
				if (!adjusting) {
					prefs.putDouble(TerrainConfig.TERRAIN_EDITOR_BASELINE, value.doubleValue());
				}
			}
		};
		baseline.setInterval(10);
		baseline.setSpinnerModel(new FloatRangeSpinnerModel(-1000, 1000, 1,
				prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_BASELINE, TerrainConfig.TERRAIN_EDITOR_BASELINE_DEFAULT)));
		baseline.setFormatterString("%1.0f");
		a.addChild(baseline);

		FancyButton setBaseToCurrent = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				if (!adjusting) {
					prefs.putDouble(TerrainConfig.TERRAIN_EDITOR_BASELINE, terrainHeight);
				}
			}
		};
		setBaseToCurrent.setText("Set");
		setBaseToCurrent.setToolTipText("Set the baseline to the elevation at the cursor position");
		a.addChild(setBaseToCurrent, "gapleft 32");

		CheckBox restrictToBaseline = new CheckBox(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				if (!adjusting) {
					prefs.putBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE, getIsChecked());
				}
			}
		};
		restrictToBaseline.setIsChecked(prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
				TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT));
		restrictToBaseline.setLabelText("Enable");
		a.addChild(restrictToBaseline);

		TerrainTemplateConfiguration cfg = getConfigurationForLocation();
		tileEnvironment.setSelectedByValue(
				cfg.getEnvironment() == null || StringUtils.isBlank(cfg.getEnvironment()) ? "" : cfg.getEnvironment(), false);

		// Actions

		Container actions = new Container(screen);
		actions.setLayoutManager(new MigLayout(screen, "wrap 5", "[shrink 0,grow][shrink 0][][shrink 0][]", "[]"));

		// Flatten
		newElevation = new Spinner<Float>(screen);
		newElevation.setSpinnerModel(new FloatRangeSpinnerModel(0, cfg.getMaxHeight(), 1f, 0f));
		newElevation.setInterval(50);
		newTile = new ButtonAdapter(screen, "New") {

			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				try {
					newTile(playerTile, newElevation.getSelectedValue());
				} catch (IOException ex) {
					error("Failed to create new tile.", ex);
					LOG.log(Level.SEVERE, "Failed to create new tile.", ex);
				}
			}

		};
		newTile.setToolTipText("Create a new tile at the specified elevation and current camera location");
		actions.addChild(newTile, "growx");
		actions.addChild(new Label("Elev:", screen));
		actions.addChild(newElevation, "span 3");

		// Delete
		deleteTile = new ButtonAdapter(screen, "Delete") {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
					@Override
					public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
						hideWindow();
					}

					@Override
					public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
						deleteTile(playerTile);
						hideWindow();
					}
				};
				dialog.setDestroyOnHide(true);
				dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
				dialog.setWindowTitle("Confirm Delete");
				dialog.setButtonOkText("Delete");
				dialog.setMsg(String.format("Are you sure you wish to delete the tile at %d, %d?", playerTile.x, playerTile.y));
				dialog.setIsResizable(false);
				dialog.setIsMovable(false);
				dialog.sizeToContent();
				UIUtil.center(screen, dialog);
				screen.addElement(dialog, null, true);
				dialog.showAsModal(true);
			}
		};
		deleteTile.setToolTipText("Deletes tile at camera location");
		actions.addChild(deleteTile, "growx,wrap");

		// Smooth
		final Spinner<Integer> radius = new Spinner<Integer>(screen);
		radius.setSpinnerModel(new IntegerRangeSpinnerModel(1, 129, 1, 1));
		final Spinner<Float> factor = new Spinner<Float>(screen);
		factor.setSpinnerModel(new FloatRangeSpinnerModel(0, 1f, 0.1f, 0.5f));

		ButtonAdapter smoothTile = new ButtonAdapter(screen, "Smooth") {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				TerrainInstance instance = terrainLoader.get(playerTile);
				if (instance == null || instance.getTerrainTemplate() == null) {
					error("No such tile");
					return;
				}
				undoManager.storeAndExecute(new SmoothTile(instance, terrainLoader, factor.getSpinnerModel().getCurrentValue(),
						radius.getSpinnerModel().getCurrentValue()));
			}
		};
		actions.addChild(smoothTile, "growx");
		actions.addChild(new Label("Fac:", screen));
		actions.addChild(factor);
		actions.addChild(new Label("Rad:", screen));
		actions.addChild(radius);

		// Flatten
		final Spinner<Integer> amount = new Spinner<Integer>(screen);
		amount.setSpinnerModel(new IntegerRangeSpinnerModel(1, 255, 1, 128));
		ButtonAdapter flattenTile = new ButtonAdapter(screen, "Flatten") {

			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				TerrainInstance instance = terrainLoader.get(playerTile);
				if (instance == null || instance.getTerrainTemplate() == null) {
					error("No such tile");
					return;
				}
				undoManager.storeAndExecute(
						new FlattenTile(instance, terrainLoader, amount.getSpinnerModel().getCurrentValue().byteValue()));
			}

		};
		flattenTile.setToolTipText(
				"Flattens out the valleys. The flatten algorithm makes the valleys more prominent while keeping the hills mostly intact");
		actions.addChild(flattenTile, "growx");
		actions.addChild(new Label(screen));
		actions.addChild(amount, "span 3");

		// Erode
		ButtonAdapter erode = new ButtonAdapter(screen, "Erode") {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				TerrainInstance instance = terrainLoader.get(playerTile);
				if (instance == null || instance.getTerrainTemplate() == null || instance.getHeightmap() == null) {
					error("No such tile");
					return;
				}
				undoManager.storeAndExecute(new ErodeTile(instance, terrainLoader));
			}
		};
		erode.setToolTipText("Applies the FIR filter to a given height map. This simulates water errosion.");
		actions.addChild(erode, "growx");

		selectTab.addChild(actions, "growx, span 2");
		selectTab.addChild(new XSeparator(screen, Orientation.HORIZONTAL), "growx, span 2");

		Container c = new Container(screen);
		c.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[][]", "[][][][]"));
		c.addChild(new Label("Total:", screen));
		c.addChild(totalMemory = new Label("", screen));
		c.addChild(new Label("Free:", screen));
		c.addChild(freeMemory = new Label("", screen));
		c.addChild(new Label("Max:", screen));
		c.addChild(maxMemory = new Label("", screen));
		c.addChild(new Label("Used:", screen));
		c.addChild(usedMemory = new Label("", screen));
		c.addChild(new Label("Undos:", screen));
		c.addChild(undos = new Label("", screen));

		// Memory
		FancyButton gc = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				System.gc();
			}
		};
		gc.setText("Force GC");
		c.addChild(gc, "span 2, ax 50%");
		selectTab.addChild(a, "growx, span 2");
		setMemoryValues();
		selectTab.addChild(c, "growx, span 2");

		return selectTab;
	}

	private void setMemoryValues() {
		totalMemory.setText(formatMemory(Runtime.getRuntime().totalMemory()));
		freeMemory.setText(formatMemory(Runtime.getRuntime().freeMemory()));
		maxMemory.setText(formatMemory(Runtime.getRuntime().maxMemory()));
		usedMemory.setText(formatMemory(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
		undos.setText(undoManager.undoSize() + " / " + undoManager.redoSize());
	}

	private String formatMemory(long val) {
		return (val / 1024 / 1024) + "MiB";
	}

	private void removeFileIfExists(File file) {
		LOG.info(String.format("Will remove %s", file.getAbsolutePath()));
		if (file.exists()) {
			if (file.delete()) {
				info(String.format("Removed %s", file.getName()));
			} else {
				error(String.format("Failed to remove %s", file.getName()));
			}
		}
	}

	private void newTile(PageLocation tile, float height) throws IOException {
		final TerrainTemplateConfiguration terrainTemplate = terrainLoader.getDefaultTerrainTemplate();

		// Heightmap
		int pageSize = terrainTemplate.getPageSize();
		Image img = new Image(Image.Format.Luminance16, pageSize, pageSize, ByteBuffer.allocateDirect(pageSize * pageSize * 2));
		SaveableWideImageBasedHeightMap ibhm = new SaveableWideImageBasedHeightMap(img,
				65535f / (float) terrainTemplate.getMaxHeight());
		try {
			ibhm.setSize(pageSize);
		} catch (Exception e) {
			throw new IOException(e);
		}
		ibhm.setHeightData(new float[pageSize * pageSize]);
		ibhm.fill(height);
		TerrainInstance ti = new TerrainInstance(tile, terrainTemplate);
		ti.setHeightmap(ibhm);
		saveHeightmap(ti);

		// Coverage
		img = new Image(Image.Format.ABGR8, 256, 256, ByteBuffer.allocateDirect(256 * 256 * 4));
		ImagePainter p = new ImagePainter(img);
		p.wipe(new ColorRGBA(0, 0, 0, 1));
		FileOutputStream out = new FileOutputStream(app.getAssets().getExternalAssetFile(format("%s/%s",
				terrainTemplate.getAssetFolder(), format(terrainTemplate.getTextureCoverageFormat(), tile.x, tile.y))));
		try {
			PNGSaver.save(out, false, true, img);
		} finally {
			out.close();
		}

		// Reset main texture cache
		((ServerAssetManager) app.getAssetManager()).clearCache();

		//
		terrainLoader.unload(tile);
		queueTerrainPagesLoad();

		// Message
		info(String.format("Created tile %d, %d (at elev. %4.2f)", playerTile.x, playerTile.y, terrainHeight));
	}

	private void deleteTile(PageLocation tile) {
		TerrainInstance instance = terrainLoader.get(tile);
		if (instance == null || instance.getTerrainTemplate() == null) {
			error("No such tile");
			return;
		}

		for (String s : new String[] { instance.getTerrainTemplate().getHeightmapImageFormat(),
				instance.getTerrainTemplate().getTextureBaseFormat(), instance.getTerrainTemplate().getTextureCoverageFormat(),
				instance.getTerrainTemplate().getTextureTextureFormat(), instance.getTerrainTemplate().getPerPageConfig() }) {
			removeFileIfExists(app.getAssets().getExternalAssetFile(
					format("%s/%s", instance.getTerrainTemplate().getAssetFolder(), format(s, tile.x, tile.y))));
		}

		// Reset main texture cache
		((ServerAssetManager) app.getAssetManager()).clearCache();

		// Remove from configuration cache
		TerrainTemplateConfiguration.remove(instance.getTerrainTemplate());

		// Unload from scene
		terrainLoader.unload(tile);

		// Message
		info(String.format("Deleted tile %d, %d", playerTile.x, playerTile.y));
		setAvailable();
	}

	private TabPanelContent flattenTab() {
		TabPanelContent toolOptions = new ModeTab(screen, TerrainEditorMode.FLATTEN);
		toolOptions.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[][fill, grow]", "[][][]push"));

		// Brush Size
		toolOptions.addChild(new Label("Brush Size:", screen));
		flattenSize = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false) {
			@Override
			public void onChange(Integer value) {
				prefs.putInt(TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE, value);
			}
		};
		flattenSize.setInterval(10);
		flattenSize.setSpinnerModel(flattenSizeModel);
		toolOptions.addChild(flattenSize);

		// Brush strength
		toolOptions.addChild(new Label("Elevation:", screen));
		flattenElevation = new Spinner<Float>(screen, Orientation.HORIZONTAL, false) {
			@Override
			public void onChange(Float value) {
				recreateFlattenBrush();
				cursorSpatial.setLocalTranslation(cursor.x, flattenElevationModel.getCurrentValue(), cursor.y);
			}
		};
		flattenElevation.setInterval(100f);
		flattenElevation.setSpinnerModel(flattenElevationModel);
		setFlattenElevation();
		toolOptions.addChild(flattenElevation);

		// Set now
		setElevation = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				if (!adjusting && playerTile != null) {
					paintAtCursor(0);
				}
			}
		};
		setElevation.setText("Set");
		toolOptions.addChild(setElevation, "span 2, ax 50%");

		recreateFlattenBrush();

		// Message
		// Label Label = new
		// Label("Click to select some terrain to determine height to flatten
		// to (or use above spinner), then drag the cursor ball to flatten.",
		// screen);
		// Label.setTextWrap(LineWrapMode.Word);
		// toolOptions.addChild(Label, "span 2");
		return toolOptions;
	}

	private TabPanelContent liquidTab() {

		TabPanelContent contentArea = new TabPanelContent(screen);
		contentArea.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[][fill, grow]", "[][]push"));

		liquidPlane = new ComboBox<String>(screen) {
			@Override
			public void onChange(int selectedIndex, String value) {
				if (!adjusting && playerTile != null) {
					TerrainInstance page = getPage(playerTile);
					TerrainTemplateConfiguration configuration = page.getTerrainTemplate();
					final boolean selectedNone = value.equals("None");
					elevation.setIsEnabled(!selectedNone);
					if (selectedNone && configuration.getLiquidPlaneConfiguration() != null) {
						undoManager.storeAndExecute(new SetLiquidPlane(page, null, terrainLoader));
					} else if (!selectedNone) {
						if (configuration.getLiquidPlaneConfiguration() == null) {
							Camera cam = app.getCamera();
							float el = cam.getLocation().y - 10;
							undoManager.storeAndExecute(new SetLiquidPlane(page,
									new TerrainTemplateConfiguration.LiquidPlaneConfiguration(el, value), terrainLoader));
							elevation.setSelectedValue(el);
						} else {
							// Hopefully the slider will be somewhere sensible
							// :)
							undoManager.storeAndExecute(new SetLiquidPlaneMaterial(page, value, terrainLoader));
						}
					}
				}
			}
		};
		for (String s : Arrays.asList("None", "Water", "Lava", "Tropical", "Shard", "SwampWater", "Tar")) {
			liquidPlane.addListItem(s, s);
		}
		contentArea.addChild(new Label("Plane:", screen));
		contentArea.addChild(liquidPlane);
		contentArea.addChild(new Label("Elevation:", screen));

		elevation = new Spinner<Float>(screen, Orientation.HORIZONTAL, true) {
			@Override
			public void onChange(Float value) {
				if (!adjusting && playerTile != null) {
					TerrainInstance page = getPage(playerTile);
					if (page != null) {
						final TerrainTemplateConfiguration.LiquidPlaneConfiguration liquidPlaneConfiguration = page
								.getTerrainTemplate().getLiquidPlaneConfiguration();
						if (liquidPlaneConfiguration != null) {
							undoManager.storeAndExecute(new SetLiquidElevation(page, value, terrainLoader));
						}
					}
				}
			}
		};
		elevation.setInterval(10);
		elevation.setSpinnerModel(new FloatRangeSpinnerModel(-1000, 1000, 1, 0));
		elevation.setFormatterString("%1.0f");
		contentArea.addChild(elevation);

		// TODO bug -
		// Form f = new Form(screen);
		// f.addFormElement(liquidPlane);
		// f.addFormElement(elevation);

		return contentArea;
	}

	@Override
	protected void templateChanged(TerrainTemplateConfiguration templateConfiguration) {
		setAvailable();
		recreateCursor();
		setSplatTabValues();
	}

	private void recreateHeightBrush() {
		final Table.TableRow selectedRow = heightBrushTexture.getSelectedRow();
		if (selectedRow != null) {

			if (mode.equals(TerrainEditorMode.SMOOTH)) {
				heightBrush = new TerrainSmoothBrush(assetManager, undoManager, terrainLoader, sizeModel.getCurrentValue(),
						selectedRow.getValue().toString(), amountModel.getCurrentValue(),
						prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
								TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
										? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue() : Float.MIN_VALUE);
			} else {

				heightBrush = new TerrainHeightBrush(assetManager, undoManager, terrainLoader, sizeModel.getCurrentValue(),
						selectedRow.getValue().toString(),
						mode.equals(TerrainEditorMode.ERASE) ? -amountModel.getCurrentValue() : amountModel.getCurrentValue(),
						prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
								TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
										? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue() : Float.MIN_VALUE);
			}
		}
	}

	private void recreateFlattenBrush() {
		flattenBrush = new TerrainFlattenBrush(assetManager, undoManager, terrainLoader, flattenSizeModel.getCurrentValue(),
				flattenElevationModel.getCurrentValue(), "Textures/Brushes/Circle32.png", amountModel.getCurrentValue(),
				prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
						TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
								? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue() : Float.MIN_VALUE);
	}

	private void recreateSplatBrush() {
		final Table.TableRow selectedRow = splatBrushTexture.getSelectedRow();
		if (selectedRow != null) {
			splatBrush = new TerrainSplatBrush(assetManager, undoManager, terrainLoader, splatSizeModel.getCurrentValue(),
					selectedRow.getValue().toString(), TerrainSplatBrush.Channel.values()[selectedTexture],
					splatRateModel.getCurrentValue(),
					prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
							TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
									? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue() : Float.MIN_VALUE);
		}
	}

	private boolean isValidForEditing() {
		return assetManager != null && playerTile != null && playerTile.isSet();
	}

	private void setAvailable() {
		// TODO - weird bug -
		// http://hub.jmonkeyengine.org/forum/topic/consistent-setisenabled/
		// undoButton.setIsEnabled(undoManager.isUndoAvailable());
		// redoButton.setIsEnabled(undoManager.isRedoAvailable());
		if (playerTile != null) {
			TerrainInstance page = getPage(playerTile);
			final TerrainTemplateConfiguration.LiquidPlaneConfiguration config = page == null ? null
					: page.getTerrainTemplate().getLiquidPlaneConfiguration();
			deleteTile.setIsEnabled(playerTile.isValid() && page != null && page.isHeightmapAvailable());
			newTile.setIsEnabled(playerTile.isValid() && ((page == null || !page.isHeightmapAvailable())));
			elevation.setIsEnabled(playerTile.isValid() && config != null && config.getMaterial() != null);
		} else {
			deleteTile.setIsEnabled(false);
			newTile.setIsEnabled(false);
			elevation.setIsEnabled(false);
		}

	}

	private void maybeSetFlattenElevation() {
		if (!mode.equals(TerrainEditorMode.FLATTEN) && flattenElevation != null) {
			setFlattenElevation();
		}
	}

	private void setFlattenElevation() {
		flattenElevation.setSelectedValue(terrainHeight);
	}

	class ModeTab extends TabPanelContent {

		private final TerrainEditorMode mode;

		ModeTab(ElementManager screen, TerrainEditorMode mode) {
			super(screen);
			this.mode = mode;
		}

		@Override
		public void childShow() {
			super.childShow();
			TerrainEditorAppState.this.mode = mode;
			destroyCursor();
			recreateCursor();
		}
	}

	@SuppressWarnings("serial")
	class FlattenTile extends AbstractTileOp {

		private byte amount;

		FlattenTile(TerrainInstance instance, TerrainLoader loader, byte amount) {
			super(instance, loader);
			this.amount = amount;
		}

		protected void doOp(AbstractHeightMap heightmap) {
			heightmap.flatten(amount);
		}
	}

	@SuppressWarnings("serial")
	class SmoothTile extends AbstractTileOp {

		private float factor;
		private int radius;

		SmoothTile(TerrainInstance instance, TerrainLoader loader, float factor, int radius) {
			super(instance, loader);
			this.factor = factor;
			this.radius = radius;
		}

		protected void doOp(AbstractHeightMap heightmap) {
			heightmap.smooth(factor, radius);
		}
	}

	@SuppressWarnings("serial")
	abstract class AbstractTileOp implements UndoManager.UndoableCommand {

		private final TerrainInstance instance;
		private final TerrainLoader loader;
		private boolean wasNeedSave;
		private float[] wasHeightData;

		AbstractTileOp(TerrainInstance instance, TerrainLoader loader) {
			this.instance = instance;
			this.loader = loader;
		}

		public void undoCommand() {
			((SaveableHeightMap) instance.getHeightmap()).setHeightData(wasHeightData);
			((TerrainQuadWrapper) instance.getQuad()).setHeights(instance.getHeightmap().getHeightMap());
			loader.reconfigureTile(instance);
			if (!instance.setNeedsSave(wasNeedSave)) {
				instance.setNeedsSave(true);
			}
			instance.setNeedsEdgeFix(true);
			wasHeightData = null;
		}

		public void doCommand() {
			AbstractHeightMap heightmap = instance.getHeightmap();
			float[] hm = heightmap.getHeightMap();
			wasHeightData = new float[hm.length];
			System.arraycopy(hm, 0, wasHeightData, 0, hm.length);

			doOp(heightmap);
			((TerrainQuadWrapper) instance.getQuad()).setHeights(hm);
			// instance.getQuad().recalculateAllNormals();
			wasNeedSave = instance.setNeedsSave(true);
			instance.setNeedsEdgeFix(true);
			instance.setNeedsSave(wasNeedSave);
			loader.reconfigureTile(instance);
		}

		protected abstract void doOp(AbstractHeightMap heightmap);
	}

	@SuppressWarnings("serial")
	class ErodeTile extends AbstractTileOp {

		ErodeTile(TerrainInstance instance, TerrainLoader loader) {
			super(instance, loader);
		}

		protected void doOp(AbstractHeightMap heightmap) {
			heightmap.erodeTerrain();
		}
	}

	@SuppressWarnings("serial")
	class SetLiquidPlane implements UndoManager.UndoableCommand {

		private final TerrainTemplateConfiguration.LiquidPlaneConfiguration liquid;
		private final TerrainInstance instance;
		private final TerrainLoader loader;
		private TerrainTemplateConfiguration.LiquidPlaneConfiguration current;
		private boolean wasNeedSave;

		SetLiquidPlane(TerrainInstance instance, TerrainTemplateConfiguration.LiquidPlaneConfiguration liquid,
				TerrainLoader loader) {
			this.liquid = liquid;
			this.instance = instance;
			this.loader = loader;
		}

		public void undoCommand() {
			instance.getTerrainTemplate().setLiquidPlaneConfiguration(current);
			loader.reconfigureTile(instance);
			if (!instance.setNeedsSave(wasNeedSave)) {
				instance.setNeedsSave(true);
			}
			current = null;
		}

		public void doCommand() {
			this.current = instance.getTerrainTemplate().getLiquidPlaneConfiguration();
			instance.getTerrainTemplate().setLiquidPlaneConfiguration(liquid);
			wasNeedSave = instance.setNeedsSave(true);
			instance.setNeedsSave(wasNeedSave);
			loader.reconfigureTile(instance);
		}
	}

	@SuppressWarnings("serial")
	class SetLiquidPlaneMaterial implements UndoManager.UndoableCommand {

		private final String newMaterial;
		private final TerrainInstance instance;
		private final TerrainLoader loader;
		private String current;
		private boolean wasNeedSave;

		SetLiquidPlaneMaterial(TerrainInstance instance, String newMaterial, TerrainLoader loader) {
			this.newMaterial = newMaterial;
			this.instance = instance;
			this.loader = loader;
		}

		public void undoCommand() {
			instance.getTerrainTemplate().getLiquidPlaneConfiguration().setMaterial(current);
			if (!instance.setNeedsSave(wasNeedSave)) {
				instance.setNeedsSave(true);
			}
			loader.reconfigureTile(instance);
			current = null;
		}

		public void doCommand() {
			this.current = instance.getTerrainTemplate().getLiquidPlaneConfiguration().getMaterial();
			instance.getTerrainTemplate().getLiquidPlaneConfiguration().setMaterial(newMaterial);
			wasNeedSave = instance.setNeedsSave(true);
			loader.reconfigureTile(instance);
		}
	}

	@SuppressWarnings("serial")
	class SetLiquidElevation implements UndoManager.UndoableCommand {

		private final float newElevation;
		private final TerrainInstance instance;
		private final TerrainLoader loader;
		private float current;
		private boolean wasNeedSave;

		SetLiquidElevation(TerrainInstance instance, float newElevation, TerrainLoader loader) {
			this.newElevation = newElevation;
			this.instance = instance;
			this.loader = loader;
		}

		public void undoCommand() {
			instance.getTerrainTemplate().getLiquidPlaneConfiguration().setElevation(current);
			loader.reconfigureTile(instance);
			onElevationChange();
			final ClutterAppState state = stateManager.getState(ClutterAppState.class);
			if (state != null) {
				state.timedReload();
			}
			if (!instance.setNeedsSave(wasNeedSave)) {
				instance.setNeedsSave(true);
			}
			current = Float.MIN_VALUE;
		}

		public void doCommand() {
			this.current = instance.getTerrainTemplate().getLiquidPlaneConfiguration().getElevation();
			instance.getTerrainTemplate().getLiquidPlaneConfiguration().setElevation(newElevation);
			loader.reconfigureTile(instance);
			wasNeedSave = instance.setNeedsSave(true);
			onElevationChange();
			final ClutterAppState state = stateManager.getState(ClutterAppState.class);
			if (state != null) {
				state.timedReload();
			}
		}
	}
}
