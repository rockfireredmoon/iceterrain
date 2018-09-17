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
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import org.icescene.SceneConfig;
import org.icescene.SceneConstants;
import org.icescene.assets.Assets;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.configuration.TerrainTemplateConfiguration.LiquidPlane;
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
import org.iceui.actions.ActionAppState;
import org.iceui.actions.ActionMenu;
import org.iceui.actions.AppAction;
import org.iceui.actions.AppAction.Style;
import org.iceui.controls.ElementStyle;
import org.iceui.controls.TabPanelContent;

import com.jme3.app.state.AppStateManager;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapFont.Align;
import com.jme3.font.BitmapFont.VAlign;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
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
import icetone.controls.buttons.ButtonGroup;
import icetone.controls.buttons.CheckBox;
import icetone.controls.buttons.PushButton;
import icetone.controls.buttons.RadioButton;
import icetone.controls.containers.TabControl;
import icetone.controls.containers.TabControl.TabButton;
import icetone.controls.extras.Separator;
import icetone.controls.lists.ComboBox;
import icetone.controls.lists.FloatRangeSpinnerModel;
import icetone.controls.lists.IntegerRangeSpinnerModel;
import icetone.controls.lists.Spinner;
import icetone.controls.table.Table;
import icetone.controls.table.TableCell;
import icetone.controls.table.TableRow;
import icetone.controls.text.Label;
import icetone.core.Container;
import icetone.core.BaseElement;
import icetone.core.BaseScreen;
import icetone.core.Form;
import icetone.core.Orientation;
import icetone.core.Size;
import icetone.core.StyledContainer;
import icetone.core.ToolKit;
import icetone.core.ZPriority;
import icetone.core.event.UIChangeListener;
import icetone.core.layout.Border;
import icetone.core.layout.BorderLayout;
import icetone.core.layout.FlowLayout;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.mig.MigLayout;
import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;
import icetone.core.utils.Alarm;
import icetone.extras.windows.DialogBox;
import icetone.extras.windows.PersistentWindow;
import icetone.extras.windows.SaveType;
import icetone.fontawesome.FontAwesome;

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
	private StyledContainer layer;
	private UndoManager.ListenerAdapter undoListener;
	private PushButton newTile;
	private PushButton deleteTile;
	private PushButton saveEnv;
	private PushButton resetEnv;
	private int lastMods;
	private TabControl tabs;
	private RadioButton<Integer> raise;
	private RadioButton<Integer> lower;
	private Spinner<Integer> flattenSize;
	private Spinner<Integer> splatSize;
	private Spinner<Float> splatRate;
	private Spinner<Float> amount;
	private Spinner<Integer> size;
	private Table heightBrushTexture;
	private Table splatBrushTexture;
	// private CheckBox wireframe;
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
	private ButtonGroup<RadioButton<Integer>> splatGroup;
	private int selectedTexture;
	final static Logger LOG = Logger.getLogger(TerrainEditorAppState.class.getName());
	private SplatControl texture0;
	private SplatControl texture1;
	private SplatControl texture2;
	private SplatControl texture3;
	private PersistentWindow terrainEditWindow;
	private Collection<String> imageResources;
	private ComboBox<LiquidPlane> liquidPlane;
	private Spinner<Float> elevation;
	private Spinner<Float> baseline;
	private TerrainEditorMode mode = TerrainEditorMode.SELECT;
	private Vector2f cursor = new Vector2f(0, 0);
	private PushButton setElevation;
	// private CheckBox snapToQuad;
	private ComboBox<String> tileEnvironment;
	private Geometry gridGeom;
	private int paints;
	private RadioButton<Integer> smooth;
	private Node arrowSpatial;
	private ModeTab selectTab;
	private Spinner<Float> newElevation;
	private ActionMenu editMenu;
	private AppAction undoAction;
	private AppAction redoAction;
	private ActionMenu viewMenu;
	private AppAction wireframeAction;
	private AppAction snapToQuadAction;

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
						viewer.getPreferences(), viewer.light, viewer.mappableNode, viewer.propFactory,
						viewer.worldNode, viewer.mouseManager);
				terrainEditorAppState.playerTile = viewer.playerTile;
				terrainEditorAppState.viewLocation = viewer.viewLocation;
				stateManager.attach(terrainEditorAppState);
				return terrainEditorAppState;
			}
		} else {
			// We current have an editor, make a viewer
			LOG.info("Switching to terrain viewer");
			stateManager.detach(editor);
			final TerrainAppState terrainAppState = new TerrainAppState(editor.terrainLoader, editor.prefs,
					editor.light, editor.mappableNode, editor.propFactory, editor.worldNode, editor.mouseManager);
			terrainAppState.playerTile = editor.playerTile;
			terrainAppState.viewLocation = editor.viewLocation;
			stateManager.attach(terrainAppState);
			return terrainAppState;
		}
	}

	public TerrainEditorAppState(TerrainLoader terrainLoader, Preferences prefs, EnvironmentLight light,
			Node mappableNode, EntityFactory propFactory, Node worldNode, MouseManager mouseManager) {
		super(terrainLoader, prefs, light, mappableNode, propFactory, worldNode, mouseManager);
		addPrefKeyPattern(TerrainConfig.TERRAIN_EDITOR + ".*");
	}

	@Override
	protected void beforeTerrainInitialize() {
		LOG.info("Initalizing terrain editing");

		amountModel = new FloatRangeSpinnerModel(0.1f, 50, 0.1f, prefs.getFloat(
				TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT, TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT_DEFAULT));
		sizeModel = new IntegerRangeSpinnerModel(1, 32, 1, prefs.getInt(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE,
				TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE_DEFAULT));
		splatSizeModel = new IntegerRangeSpinnerModel(1, 32, 1, prefs.getInt(
				TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE, TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE_DEFAULT));
		splatRateModel = new FloatRangeSpinnerModel(0, 20, 0.1f, prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE,
				TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE_DEFAULT));
		flattenElevationModel = new FloatRangeSpinnerModel(-9999, 9999, 1f, 0f);
		flattenSizeModel = new IntegerRangeSpinnerModel(1, 32, 1,
				prefs.getInt(TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE,
						TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE_DEFAULT));

		terrainLoader.setReadOnly(false);
		recreateCursor();

		// Layer for some extra on screen buttons
		layer = new StyledContainer(screen);
		layer.setLayoutManager(new MigLayout(screen, "fill", "[][]push[]", "[]push"));
		app.getLayers(ZPriority.NORMAL).addElement(layer);

		// Undo
		ActionAppState actions = app.getStateManager().getState(ActionAppState.class);
		if (actions != null) {
			actions.getMenuBar().addActionMenu(editMenu = new ActionMenu("Edit", 5));
			actions.getMenuBar().addActionMenu(viewMenu = new ActionMenu("View", 7));
			actions.getMenuBar().addAction(undoAction = new AppAction("Undo", evt -> maybeDoUndo()).setMenu("Edit")
					.setInterval(SceneConstants.UNDO_REDO_REPEAT_INTERVAL));
			actions.getMenuBar().addAction(redoAction = new AppAction("Redo", evt -> maybeDoRedo()).setMenu("Edit")
					.setInterval(SceneConstants.UNDO_REDO_REPEAT_INTERVAL));
			actions.getMenuBar().addAction(wireframeAction = new AppAction("Wireframe", evt -> {
				prefs.putBoolean(TerrainConfig.TERRAIN_WIREFRAME, evt.getSourceAction().isActive());
			}).setMenu("View").setStyle(Style.TOGGLE).setActive(
					prefs.getBoolean(TerrainConfig.TERRAIN_WIREFRAME, TerrainConfig.TERRAIN_WIREFRAME_DEFAULT)));
			actions.getMenuBar().addAction(snapToQuadAction = new AppAction("Snap To Quad", evt -> {
				prefs.putBoolean(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD, evt.getSourceAction().isActive());
			}).setMenu("View").setStyle(Style.TOGGLE).setActive(prefs.getBoolean(
					TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD, TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD_DEFAULT)));
		}

		// TODO totally bizarre, but without this last empty element , the
		// penultimate element gets resized wrong
		layer.addElement(new Label(screen));

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
		imageResources = ((ServerAssetManager) app.getAssetManager())
				.getAssetNamesMatching(".*\\.png|.*\\.jpg|.*\\.jpeg|.*\\.gif");
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

		ActionAppState actions = app.getStateManager().getState(ActionAppState.class);
		if (actions != null) {
			actions.getMenuBar().invalidate();
			actions.getMenuBar().removeAction(undoAction);
			actions.getMenuBar().removeAction(redoAction);
			actions.getMenuBar().removeActionMenu(editMenu);
			actions.getMenuBar().removeAction(wireframeAction);
			actions.getMenuBar().removeAction(snapToQuadAction);
			actions.getMenuBar().removeActionMenu(viewMenu);
			actions.getMenuBar().validate();
		}

		statsUpdateTask.cancel();
		app.getLayers(ZPriority.NORMAL).removeElement(layer);

		undoManager.removeListener(undoListener);
		app.getKeyMapManager().removeListener(this);
		app.getKeyMapManager().deleteMapping(MAPPING_UNDO);
		app.getKeyMapManager().deleteMapping(MAPPING_REDO);

		mods.removeListener(this);
		mouseManager.setRepeatDelay(oldRepeatDelay);
		mouseManager.setRepeatInterval(oldRepeatInterval);
		mouseManager.removeListener(this);
		mouseManager.setMode(oldMode);
		terrainEditWindow.destroy();
		terrainLoader.setReadOnly(true);
	}

	@Override
	public void playerTileChanged(PageLocation loc) {
		super.playerTileChanged(loc);
		recreateCursor();
		if (selectTab != null && loc != null)
			selectTab.setEnabled(loc.isValid());
		if (terrainEditWindow != null) {
			terrainEditWindow.setWindowTitle(String.format("Terrain - %d,%d", loc.x, loc.y));
			setSplatTabValues();
		}
		TerrainInstance page = getPage(loc);
		if (page != null) {
			TerrainTemplateConfiguration terrainTemplate = page.getTerrainTemplate();
			String environment = terrainTemplate.getEnvironment();
			tileEnvironment.runAdjusting(() -> tileEnvironment
					.setSelectedByValue(environment == null || StringUtils.isBlank(environment) ? "" : environment));
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

	public void click(MouseManager manager, Spatial spatial, ModifierKeysAppState mods, int startModsMask,
			Vector3f contactPoint, CollisionResults results, float tpf, boolean repeat) {
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
		screen.resetKeyboardFocus(null);
	}

	public void dragEnd(MouseManager manager, Spatial spatial, ModifierKeysAppState mods, int startModsMask) {
		operationEnded();
	}

	public void dragStart(Vector3f click3d, MouseManager manager, Spatial spatial, ModifierKeysAppState mods,
			Vector3f direction) {
		paints = 0;
	}

	public void drag(MouseManager manager, Spatial spatial, ModifierKeysAppState mods, Vector3f click3d,
			Vector3f lastClick3d, float tpf, int startModsMask, CollisionResults results, Vector3f lookDir) {
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
			raise.setState(true);
			break;
		case ERASE:
			tabs.setSelectedTabIndex(1);
			lower.setState(true);
			break;
		case FLATTEN:
			tabs.setSelectedTabIndex(2);
			break;
		case SMOOTH:
			tabs.setSelectedTabIndex(1);
			smooth.setState(true);
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
		app.getExtendedFlyByCamera().setAllowRiseAndLower(!mods.isCtrl());

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

				// t.copyQuadHeightmapToStoredHeightmap();
				// if (terrainLoader.syncEdges(t)) {
				// Icelib.dumpTrace();
				// LOG.info("Operation ended. There are edges to sync,
				// refreshing from heightmap data");
				// t.copyStoredHeightmapToQuad();
				// }
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
					wireframeAction.setActive(
							prefs.getBoolean(TerrainConfig.TERRAIN_WIREFRAME, TerrainConfig.TERRAIN_WIREFRAME_DEFAULT));
					return null;
				}
			});
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD)) {
			// This is put on the queue to work around a tonegodgui bug
			app.enqueue(new Callable<Void>() {
				@Override
				public Void call() throws Exception {
					snapToQuadAction.setActive(prefs.getBoolean(TerrainConfig.TERRAIN_SNAP_BRUSH_TO_QUAD,
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
			splatRate.setSelectedValue(prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE,
					TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE_DEFAULT));
			recreateSplatBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE)) {
			size.setSelectedValue(prefs.getInt(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE,
					TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE_DEFAULT));
			destroyCursor();
			recreateCursor();
			recreateHeightBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT)) {
			amount.setSelectedValue(prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT,
					TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT_DEFAULT));
			recreateHeightBrush();
		} else if (evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_BASELINE)
				|| evt.getKey().equals(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE)) {
			checkBaselineGrid();
			baseline.runAdjusting(() -> baseline.getSpinnerModel().setValueFromString(String.valueOf(prefs
					.getFloat(TerrainConfig.TERRAIN_EDITOR_BASELINE, TerrainConfig.TERRAIN_EDITOR_BASELINE_DEFAULT))));
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
					prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_BASELINE, TerrainConfig.TERRAIN_EDITOR_BASELINE_DEFAULT)
							+ 0.2f,
					cursor.y - (BASELINE_GRID_SIZE / 2));
		}

	}

	private void cancelUndoRepeat() {
		if (undoRepeatTask != null) {
			undoRepeatTask.cancel();
		}
	}

	private Table createBrushSelector(final UIChangeListener<Table, Map<Integer, List<Integer>>> onChange) {
		Table brushSelector = new Table(screen);
		brushSelector.onChanged(onChange);
		brushSelector.setSelectionMode(Table.SelectionMode.ROW);
		brushSelector.addColumn("Image");
		brushSelector.addColumn("Name");
		brushSelector.setHeadersVisible(false);
		for (String a : ((ServerAssetManager) app.getAssetManager())
				.getAssetNamesMatching("Textures/Brushes/.*\\.png")) {
			TableRow r = new TableRow(screen, brushSelector, a);
			TableCell c1 = new TableCell(screen, a);
			c1.setPreferredDimensions(new Size(32, 32));
			r.addElement(c1);
			BaseElement img = new BaseElement(screen, new Size(32, 32));
			img.setTexture(a);
			img.setIgnoreMouse(true);
			c1.addElement(img);
			TableCell c2 = new TableCell(screen, Icelib.getBaseFilename(a), a);
			c2.setPreferredDimensions(new Size(32, 40));
			r.addElement(c2);
			brushSelector.addRow(r);
		}
		if (brushSelector.getRowCount() > 0) {
			brushSelector.runAdjusting(() -> brushSelector.setSelectedRowIndex(0));
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
		if (snapToQuadAction != null && snapToQuadAction.isActive()) {
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
			terrainHeight = terrainInstance == null ? Float.MIN_VALUE
					: terrainInstance.getHeightAtWorldPosition(cursor);
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
		if (!isInitialized())
			return;

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
		if (cursorSpatial != null)
			cursorSpatial.removeFromParent();
		cursorSpatial = null;
	}

	private void setSplatTabValues() {
		try {
			TerrainInstance page = playerTile == null ? null : getPage(playerTile);

			if (page != null) {
				final TerrainTemplateConfiguration configuration = page.getTerrainTemplate();
				final TerrainTemplateConfiguration.LiquidPlaneConfiguration config = configuration
						.getLiquidPlaneConfiguration();
				if (config != null && config.getMaterial() != null) {
					final LiquidPlane liquidName = config.getMaterial();
					LOG.info(String.format("Selecting liquid %s", liquidName));
					liquidPlane.runAdjusting(() -> liquidPlane.setSelectedByValue(liquidName));
					elevation.runAdjusting(() -> elevation.setSelectedValue(config.getElevation()));
					elevation.setEnabled(true);
				} else {
					noLiquid();
				}
			} else {
				noLiquid();
			}

			if (texture0 != null) {
				if (page != null) {
					final TerrainTemplateConfiguration configuration = page.getTerrainTemplate();
					texture0.setEnabled(true);
					texture0.setValue(
							SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting0());
					texture1.setEnabled(true);
					texture1.setValue(
							SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting1());
					texture2.setEnabled(true);
					texture2.setValue(
							SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting2());
					texture3.setEnabled(true);
					texture3.setValue(
							SceneConstants.TERRAIN_PATH + "/Terrain-Common/" + configuration.getTextureSplatting3());
				} else {
					texture0.setEnabled(false);
					texture1.setEnabled(false);
					texture2.setEnabled(false);
					texture3.setEnabled(false);
				}
			}

		} finally {
			setAvailable();
		}

	}

	private void rescheduleStatsUpdate() {
		statsUpdateTask = app.getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				setMemoryValues();
				if (isEnabled())
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
		liquidPlane.runAdjusting(() -> liquidPlane.setSelectedByValue(LiquidPlane.NONE));
		elevation.setSelectedValue(0f);
		elevation.setEnabled(false);
	}

	private void terrainEditWindow() {
		terrainEditWindow = new PersistentWindow(screen, SceneConfig.TERRAIN, VAlign.Center, Align.Right,
				new Size(300, 560), true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				closeTerrain();
			}

			@Override
			protected boolean canClose() {
				if (terrainLoader.isNeedsSave()) {
					final DialogBox dialog = new DialogBox(screen, new Vector2f(15, 15), true) {
						@Override
						public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
							hide();
						}

						@Override
						public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
							hide();
							closeTerrain();
						}
					};
					dialog.setDestroyOnHide(true);
					ElementStyle.warningColor(dialog.getDragBar());
					dialog.setWindowTitle("Confirm Close Template");
					dialog.setButtonOkText("Close");
					dialog.setText("You have unsaved edits! Are you sure you wish to close this template?");
					dialog.setModal(true);
					screen.showElement(dialog, ScreenLayoutConstraints.center);
					return false;
				}
				return true;
			}
		};
		terrainEditWindow.setMinimizable(true);
		terrainEditWindow.setWindowTitle("Terrain");
		final BaseElement contentArea = terrainEditWindow.getContentArea();
		contentArea.setLayoutManager(new BorderLayout(4, 4));

		tabs = new TabControl(screen);
		tabs.addStyleClass("editor-tabs");
		tabs.addTab(new TabButton(screen) {
			{
				FontAwesome.MOUSE_POINTER.button(24, this);
			}
		}, selectTab());

		tabs.addTab(new TabButton(screen) {
			{
				FontAwesome.PAINT_BRUSH.button(24, this);
			}
		}, paintTab());

		tabs.addTab(new TabButton(screen) {
			{
				FontAwesome.ERASER.button(24, this);
			}
		}, flattenTab());

		tabs.addTab(new TabButton(screen) {
			{
				FontAwesome.ADJUST.button(24, this);
			}
		}, splatTab());

		tabs.addTab(new TabButton(screen) {
			{
				FontAwesome.SHIP.button(24, this);
			}
		}, liquidTab());
		tabs.onChange(evt -> {
			if(evt.getNewValue() == 0 || evt.getNewValue() == 2) {
				doSetMode(TerrainEditorMode.SELECT);
			}
			else if(evt.getNewValue() == 1) {
				doSetMode(TerrainEditorMode.PAINT);
			}
			else if(evt.getNewValue() == 3) {
				doSetMode(TerrainEditorMode.SPLAT);
			}
		});
		contentArea.addElement(tabs);

		// Buttons
		saveEnv = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		saveEnv.onMouseReleased(evt -> save());
		saveEnv.setText("Save");
		FontAwesome.SAVE.button(24, saveEnv);

		resetEnv = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		resetEnv.onMouseReleased(evt -> {
			if (!evt.getElement().isAdjusting() && playerTile != null) {
				TerrainInstance page = getPage(playerTile);
				page.getTerrainTemplate().reset();
				reloadTerrain();
			}
		});
		resetEnv.setText("Reset");
		FontAwesome.BAN.button(24, resetEnv);

		BaseElement bottom = new BaseElement(screen);

		bottom.setLayoutManager(new FlowLayout(8, BitmapFont.Align.Center));
		bottom.addElement(saveEnv);
		bottom.addElement(resetEnv);
		contentArea.addElement(bottom, Border.SOUTH);

		screen.addElement(terrainEditWindow);
		terrainEditWindow.show();
		// terrainEditWindow.setIsResizable(false);

		recreateFlattenBrush();
		recreateHeightBrush();
		recreateSplatBrush();
		checkBaselineGrid();

		setAvailable();
	}

	private void save() {
		if (playerTile != null) {
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

		String coverageName = format(page.getTerrainTemplate().getTextureCoverageFormat(), page.getPage().x,
				page.getPage().y);
		String coveragePath = String.format("%s/%s", page.getTerrainTemplate().getAssetFolder(), coverageName);

		Image img = page.getCoverage();
		File file = Icelib.makeParent(TerrainEditorAppState.this.app.getAssets().getExternalAssetFile(coveragePath));
		LOG.info(String.format("Writing coverage of %s to %s", page, file));
		FileOutputStream fos = new FileOutputStream(file);
		try {
			PNGSaver.save(fos, false, true, img);
		} finally {
			fos.close();
		}
	}

	private void saveHeightmap(TerrainInstance page)
			throws IOException, IllegalArgumentException, FileNotFoundException {
		SaveableHeightMap hm = (SaveableHeightMap) page.getHeightmap();
		page.copyQuadHeightmapToStoredHeightmap();
		Image img = hm.getColorImage();

		// TODO depth not working?
		int depth;
		switch (img.getFormat()) {
		case Luminance16F:
			depth = 16;
			break;
		default:
			throw new IllegalArgumentException("Unknown depth for format " + img.getFormat() + ".");
		}

		String heightmapName = String.format(page.getTerrainTemplate().getHeightmapImageFormat(), page.getPage().x,
				page.getPage().y);
		String heightmapPath = String.format("%s/%s", page.getTerrainTemplate().getAssetFolder(), heightmapName);
		File heightmapFile = Icelib.makeParent(app.getAssets().getExternalAssetFile(heightmapPath));
		FileOutputStream out = new FileOutputStream(heightmapFile);
		try {
			LOG.info(String.format("Saving greyscale image based heightmap of %d x %d to %s (%d bpp %s)",
					img.getWidth(), img.getHeight(), heightmapFile, depth, img.getFormat()));
			hm.save(out, false, false);
			// saveAs16PNG(img, out);
		} finally {
			out.close();
		}
	}

	private void saveTemplate(TerrainInstance page) throws FileNotFoundException, IOException {
		String fileName = format(page.getTerrainTemplate().getPerPageConfig(), page.getPage().x, page.getPage().y);
		File file = Icelib.makeParent(TerrainEditorAppState.this.app.getAssets()
				.getExternalAssetFile(String.format("%s/%s", page.getTerrainTemplate().getAssetFolder(), fileName)));
		LOG.info(String.format("Writing %s to %s", page, file));
		FileOutputStream fos = new FileOutputStream(file);
		try {
			page.getTerrainTemplate().write(fos, true);
		} finally {
			fos.close();
		}
	}

	@SuppressWarnings("serial")
	class SetSplatCommand implements UndoableCommand {

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
		splatGroup = new ButtonGroup<RadioButton<Integer>>();

		// Splats
		toolOptions.addElement(ElementStyle.medium(new Label("Layer", screen), true, false), "span 3");

		// Texture 0
		RadioButton<Integer> t0 = new RadioButton<Integer>(screen).setValue(0);
		splatGroup.addButton(t0);
		texture0 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(
						new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 0));
			}
		};
		toolOptions.addElement(ElementStyle.normal(new Label("A", screen), true, false));
		toolOptions.addElement(f.addFormElement(t0));
		toolOptions.addElement(texture0, "growx");
		f.addFormElement(texture0);

		// Texture 1
		RadioButton<Integer> t1 = new RadioButton<Integer>(screen).setValue(1);
		splatGroup.addButton(t1);
		texture1 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(
						new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 1));
			}
		};
		toolOptions.addElement(ElementStyle.normal(new Label("R", screen), true, false));
		toolOptions.addElement(t1);
		toolOptions.addElement(texture1, "growx");
		f.addFormElement(texture1);

		// Texture 2
		RadioButton<Integer> t2 = new RadioButton<Integer>(screen).setValue(2);
		splatGroup.addButton(t2);
		texture2 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(
						new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 2));
			}
		};
		f.addFormElement(texture2);
		toolOptions.addElement(ElementStyle.normal(new Label("G", screen), true, false));
		toolOptions.addElement(t2);
		toolOptions.addElement(texture2, "growx");

		// Texture 3
		RadioButton<Integer> t3 = new RadioButton<Integer>(screen).setValue(3);
		splatGroup.addButton(t3);
		texture3 = new SplatControl(screen, prefs, imageResources) {
			@Override
			protected void onChange(String dir, String newResource) {
				undoManager.storeAndExecute(
						new SetSplatCommand(getPage(playerTile), dir, Icelib.getFilename(newResource), 3));
			}
		};
		f.addFormElement(texture3);
		toolOptions.addElement(ElementStyle.normal(new Label("B", screen), true, false));
		toolOptions.addElement(t3);
		toolOptions.addElement(texture3, "growx");

		// Actions
		StyledContainer c = new StyledContainer(new FlowLayout(4, Align.Center));
		c.addElement(new PushButton(screen, "Copy").onMouseReleased(evt -> {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw, true);
			pw.println(texture0.getValue());
			pw.println(texture1.getValue());
			pw.println(texture2.getValue());
			pw.println(texture3.getValue());
			pw.close();
			ToolKit.get().setClipboardText(sw.toString());
			info("Splat images copied to clipboard");
		}));
		c.addElement(new PushButton(screen, "Paste").onMouseReleased(evt -> {

			BufferedReader br = new BufferedReader(new StringReader(ToolKit.get().getClipboardText()));
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
		}));
		toolOptions.addElement(c, "span 3");

		// Set inital selection
		splatGroup.setSelected(selectedTexture);

		// Brush
		toolOptions.addElement(ElementStyle.medium(new Label("Brush", screen), true, false), "span 3");

		toolOptions.addElement(splatBrushTexture = createBrushSelector((evt) -> {
			if (!evt.getSource().isAdjusting())
				recreateSplatBrush();
		}), "growx, growy, span 3");

		// Other options
		StyledContainer other = new StyledContainer(screen);
		other.setLayoutManager(new MigLayout(screen, "wrap 2", "[][fill, grow]", "[][][]"));

		// Brush Size
		other.addElement(new Label("Size:", screen));
		splatSize = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false);
		splatSize.onChange(evt -> prefs.putInt(TerrainConfig.TERRAIN_EDITOR_SPLAT_BRUSH_SIZE, evt.getNewValue()));
		splatSize.setInterval(10f);
		splatSize.setSpinnerModel(splatSizeModel);
		f.addFormElement(splatSize);
		other.addElement(splatSize);

		// Brush strength
		other.addElement(new Label("Rate:", screen));
		splatRate = new Spinner<Float>(screen, Orientation.HORIZONTAL, false);
		splatRate.onChange(evt -> prefs.putFloat(TerrainConfig.TERRAIN_EDITOR_SPLAT_RATE, evt.getNewValue()));
		splatRate.setInterval(25);
		splatRate.setSpinnerModel(splatRateModel);
		f.addFormElement(splatRate);
		other.addElement(splatRate);

		//
		toolOptions.addElement(other, "span 3, growx");

		// Events

		splatGroup.onChange(evt -> {
			selectedTexture = evt.getSource().getSelected().getValue();
			recreateSplatBrush();
			recreateCursor();
		});

		return toolOptions;
	}

	private TabPanelContent paintTab() {
		final ButtonGroup<RadioButton<Integer>> paintGroup = new ButtonGroup<RadioButton<Integer>>();
		TabPanelContent toolOptions = new ModeTab(screen, TerrainEditorMode.PAINT);
		paintGroup.setSelected(mode.equals(TerrainEditorMode.PAINT) ? 0 : 1);
		toolOptions.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[100:100:][grow]",
				"[shrink 0][shrink 0][shrink 0][shrink 0][shrink 0][grow]"));

		// Brush Size
		toolOptions.addElement(new Label("Brush Size:", screen));
		size = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false);
		size.onChange(evt -> prefs.putInt(TerrainConfig.TERRAIN_EDITOR_PAINT_BRUSH_SIZE, evt.getNewValue()));
		size.setInterval(10f);
		size.setSpinnerModel(sizeModel);
		toolOptions.addElement(size);

		// Brush strength
		toolOptions.addElement(new Label("Amount:", screen));
		amount = new Spinner<Float>(screen, Orientation.HORIZONTAL, false);
		amount.onChange(evt -> prefs.putFloat(TerrainConfig.TERRAIN_EDITOR_PAINT_AMOUNT, evt.getNewValue()));
		amount.setInterval(100);
		amount.setSpinnerModel(amountModel);
		toolOptions.addElement(amount);

		raise = new RadioButton<Integer>(screen).setValue(0);
		raise.setText("Raise");
		paintGroup.addButton(raise);
		toolOptions.addElement(raise, "gapleft 32, growx, span 2");
		lower = new RadioButton<Integer>(screen).setValue(1);
		lower.setText("Lower");
		paintGroup.addButton(lower);
		toolOptions.addElement(lower, "gapleft 32, growx, span 2");
		smooth = new RadioButton<Integer>(screen).setValue(2);
		smooth.setText("Smooth");
		paintGroup.addButton(smooth);
		toolOptions.addElement(smooth, "gapleft 32, growx, span 2");

		toolOptions.addElement(heightBrushTexture = createBrushSelector(evt -> {
			if (!evt.getSource().isAdjusting())
				recreateHeightBrush();
		}), "growx, growy, span 2");

		recreateHeightBrush();
		paintGroup.onChange(evt -> {
			switch (evt.getSource().getSelected().getValue()) {
			case 0:
				doSetMode(TerrainEditorMode.PAINT);
				break;
			case 1:
				doSetMode(TerrainEditorMode.ERASE);
				break;
			case 2:
				doSetMode(TerrainEditorMode.SMOOTH);
				break;
			}
		});
		return toolOptions;
	}
	
	private void doSetMode(TerrainEditorMode mode) {
		this.mode = mode;
		recreateHeightBrush();
		recreateCursor();
	}

	private TabPanelContent selectTab() {
		selectTab = new ModeTab(screen, TerrainEditorMode.SELECT);
		selectTab.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[shrink 0][fill]", "[][][][]push[]"));

		StyledContainer a = new StyledContainer(screen);
		a.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[][]", "[]"));
		a.addElement(new Label("Tile environment:", screen));
		tileEnvironment = new ComboBox<String>(screen);
		tileEnvironment.addListItem("Default for terrain", "");
		for (String key : EnvironmentManager.get(assetManager).getEnvironments()) {
			tileEnvironment.addListItem(key, key);
		}
		a.addElement(tileEnvironment);

		a.addElement(new Label("Base:", screen));

		baseline = new Spinner<Float>(screen, Orientation.HORIZONTAL, true);
		baseline.setInterval(10);
		baseline.setSpinnerModel(new FloatRangeSpinnerModel(-1000, 1000, 1,
				prefs.getFloat(TerrainConfig.TERRAIN_EDITOR_BASELINE, TerrainConfig.TERRAIN_EDITOR_BASELINE_DEFAULT)));
		baseline.setFormatterString("%1.0f");
		a.addElement(baseline);

		PushButton setBaseToCurrent = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		setBaseToCurrent.setText("Set");
		setBaseToCurrent.setToolTipText("Set the baseline to the elevation at the cursor position");
		a.addElement(setBaseToCurrent, "gapleft 32");

		CheckBox restrictToBaseline = new CheckBox(screen);
		restrictToBaseline.setChecked(prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
				TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT));
		restrictToBaseline.setText("Enable");
		a.addElement(restrictToBaseline);

		TerrainTemplateConfiguration cfg = getConfigurationForLocation();
		tileEnvironment.setSelectedByValue(
				cfg.getEnvironment() == null || StringUtils.isBlank(cfg.getEnvironment()) ? "" : cfg.getEnvironment());

		// Actions

		StyledContainer actions = new StyledContainer(screen);
		actions.setLayoutManager(new MigLayout(screen, "wrap 2", "[shrink 0][]", "[]"));

		actions.addElement(new Label("Elev:", screen));
		newElevation = new Spinner<Float>(screen);
		newElevation.setSpinnerModel(new FloatRangeSpinnerModel(0, cfg.getMaxHeight(), 1f, 0f));
		newElevation.setInterval(50);
		actions.addElement(newElevation);

		final Spinner<Integer> radius = new Spinner<Integer>(screen);
		radius.setSpinnerModel(new IntegerRangeSpinnerModel(1, 129, 1, 1));
		final Spinner<Float> factor = new Spinner<Float>(screen);
		factor.setSpinnerModel(new FloatRangeSpinnerModel(0, 1f, 0.1f, 0.5f));
		actions.addElement(new Label("Fac:", screen));
		actions.addElement(factor);
		actions.addElement(new Label("Rad:", screen));
		actions.addElement(radius);

		final Spinner<Integer> amount = new Spinner<Integer>(screen);
		amount.setSpinnerModel(new IntegerRangeSpinnerModel(1, 255, 1, 128));
		PushButton flattenTile = new PushButton(screen, "Flatten");
		flattenTile.setToolTipText(
				"Flattens out the valleys. The flatten algorithm makes the valleys more prominent while keeping the hills mostly intact");
		actions.addElement(new Label(screen));
		actions.addElement(amount);

		// Flatten
		newTile = new PushButton(screen, "New");
		newTile.setToolTipText("Create a new tile at the specified elevation and current camera location");
		// Delete
		deleteTile = new PushButton(screen, "Delete");
		deleteTile.onMouseReleased(evt -> {
			final DialogBox dialog = new DialogBox(screen, new Vector2f(15, 15), true) {
				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					deleteTile(playerTile);
					hide();
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Confirm Delete");
			dialog.setButtonOkText("Delete");
			dialog.setText(
					String.format("Are you sure you wish to delete the tile at %d, %d?", playerTile.x, playerTile.y));
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		});
		deleteTile.setToolTipText("Deletes tile at camera location");
		// Erode
		PushButton erode = new PushButton(screen, "Erode");
		erode.setToolTipText("Applies the FIR filter to a given height map. This simulates water errosion.");

		PushButton smoothTile = new PushButton(screen, "Smooth");

		// Buttons panels
		Container buttons = new Container(
				new FlowLayout(Orientation.VERTICAL).setEqualSizeCells(true).setFill(true).setGap(4));
		buttons.addElement(newTile);
		buttons.addElement(deleteTile);
		buttons.addElement(flattenTile);
		buttons.addElement(erode);
		buttons.addElement(smoothTile);

		// Top
		Container top = new Container(new BorderLayout());
		top.addElement(buttons, Border.WEST);
		top.addElement(actions, Border.CENTER);

		selectTab.addElement(top, "growx, span 2");
		selectTab.addElement(new Separator(screen, Orientation.HORIZONTAL), "growx, span 2");

		StyledContainer c = new StyledContainer(screen);
		c.setLayoutManager(new MigLayout(screen, "wrap 2, fill", "[][]", "[][][][]"));
		c.addElement(new Label("Total:", screen));
		c.addElement(totalMemory = new Label("", screen));
		c.addElement(new Label("Free:", screen));
		c.addElement(freeMemory = new Label("", screen));
		c.addElement(new Label("Max:", screen));
		c.addElement(maxMemory = new Label("", screen));
		c.addElement(new Label("Used:", screen));
		c.addElement(usedMemory = new Label("", screen));
		c.addElement(new Label("Undos:", screen));
		c.addElement(undos = new Label("", screen));

		// Memory
		PushButton gc = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		gc.onMouseReleased(evt -> System.gc());
		gc.setText("Force GC");
		c.addElement(gc, "span 2, ax 50%");
		selectTab.addElement(a, "growx, span 2");
		setMemoryValues();
		selectTab.addElement(c, "growx, span 2");

		// Events

		setBaseToCurrent.onMouseReleased(evt -> {
			prefs.putDouble(TerrainConfig.TERRAIN_EDITOR_BASELINE, terrainHeight);
		});
		restrictToBaseline.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				prefs.putBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE, evt.getNewValue());
			}
		});
		flattenTile.onMouseReleased(evt -> {
			TerrainInstance instance = terrainLoader.get(playerTile);
			if (instance == null || instance.getTerrainTemplate() == null) {
				error("No such tile");
				return;
			}
			undoManager.storeAndExecute(
					new FlattenTile(instance, terrainLoader, amount.getSpinnerModel().getCurrentValue().byteValue()));
		});
		newTile.onMouseReleased(evt -> {
			try {
				newTile(playerTile, newElevation.getSelectedValue());
			} catch (IOException ex) {
				error("Failed to create new tile.", ex);
				LOG.log(Level.SEVERE, "Failed to create new tile.", ex);
			}
		});
		erode.onMouseReleased(evt -> {
			TerrainInstance instance = terrainLoader.get(playerTile);
			if (instance == null || instance.getTerrainTemplate() == null || instance.getHeightmap() == null) {
				error("No such tile");
				return;
			}
			undoManager.storeAndExecute(new ErodeTile(instance, terrainLoader));
		});
		smoothTile.onMouseReleased(evt -> {
			TerrainInstance instance = terrainLoader.get(playerTile);
			if (instance == null || instance.getTerrainTemplate() == null) {
				error("No such tile");
				return;
			}
			undoManager.storeAndExecute(new SmoothTile(instance, terrainLoader,
					factor.getSpinnerModel().getCurrentValue(), radius.getSpinnerModel().getCurrentValue()));
		});
		baseline.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				prefs.putDouble(TerrainConfig.TERRAIN_EDITOR_BASELINE, evt.getNewValue().doubleValue());
			}
		});
		tileEnvironment.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				TerrainTemplateConfiguration loccfg = getConfigurationForLocation();
				PageLocation page = loccfg.getPage();
				File cfgDir = TerrainEditorAppState.getDirectoryForTerrainTemplate(getApp().getAssets(), loccfg);
				File file = new File(cfgDir,
						String.format("%s_x%dy%d.nut", loccfg.getBaseTemplateName(), page.x, page.y));
				String value = evt.getNewValue();
				try {
					if (value.equals("")) {
						loccfg.setEnvironment(null);
						file.delete();
						info(String.format("Set terrain tile %d,%d to default environment for terrain.", page.x,
								page.y));
						setEnvironment(null, EnvPriority.TILE);
					} else {
						loccfg.setEnvironment(value);
						DOSWriter dw = new DOSWriter(new FileOutputStream(file));
						try {
							dw.println(String.format("TerrainTemplate.setEnvironment(\"%s\");", value));
						} finally {
							dw.close();
						}
						info(String.format("Set terrain tile %d,%d to environment '%s'.", page.x, page.y,
								loccfg.getAssetName()));
						setEnvironment(value, EnvPriority.TILE);
					}
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to set tile environment.", e);
				}
			}
		});

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
		Image img = new Image(Image.Format.Luminance16F, pageSize, pageSize,
				ByteBuffer.allocateDirect(pageSize * pageSize * 2));
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
		FileOutputStream out = new FileOutputStream(
				Icelib.makeParent(app.getAssets().getExternalAssetFile(format("%s/%s", terrainTemplate.getAssetFolder(),
						format(terrainTemplate.getTextureCoverageFormat(), tile.x, tile.y)))));
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
				instance.getTerrainTemplate().getTextureBaseFormat(),
				instance.getTerrainTemplate().getTextureCoverageFormat(),
				instance.getTerrainTemplate().getTextureTextureFormat(),
				instance.getTerrainTemplate().getPerPageConfig() }) {
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
		toolOptions.addElement(new Label("Brush Size:", screen));
		flattenSize = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false);
		flattenSize.onChange(evt -> prefs.putInt(TerrainConfig.TERRAIN_EDITOR_FLATTEN_BRUSH_SIZE, evt.getNewValue()));
		flattenSize.setInterval(10);
		flattenSize.setSpinnerModel(flattenSizeModel);
		toolOptions.addElement(flattenSize);

		// Brush strength
		toolOptions.addElement(new Label("Elevation:", screen));
		flattenElevation = new Spinner<Float>(screen, Orientation.HORIZONTAL, false);
		flattenElevation.onChange(evt -> {
			recreateFlattenBrush();
			cursorSpatial.setLocalTranslation(cursor.x, flattenElevationModel.getCurrentValue(), cursor.y);
		});
		flattenElevation.setInterval(100f);
		flattenElevation.setSpinnerModel(flattenElevationModel);
		setFlattenElevation();
		toolOptions.addElement(flattenElevation);

		// Set now
		setElevation = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		setElevation.onMouseReleased(evt -> {
			if (playerTile != null) {
				paintAtCursor(0);
			}
		});
		setElevation.setText("Set");
		toolOptions.addElement(setElevation, "span 2, ax 50%");

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

		liquidPlane = new ComboBox<LiquidPlane>(screen, LiquidPlane.values());
		liquidPlane.onChange(evt -> {
			if (!evt.getSource().isAdjusting() && playerTile != null) {
				TerrainInstance page = getPage(playerTile);
				TerrainTemplateConfiguration configuration = page.getTerrainTemplate();
				final boolean selectedNone = evt.getNewValue().equals("None");
				elevation.setEnabled(!selectedNone);
				if (selectedNone && configuration.getLiquidPlaneConfiguration() != null) {
					undoManager.storeAndExecute(new SetLiquidPlane(page, null, terrainLoader));
				} else if (!selectedNone) {
					if (configuration.getLiquidPlaneConfiguration() == null) {
						Camera cam = app.getCamera();
						float el = cam.getLocation().y - 10;
						undoManager.storeAndExecute(new SetLiquidPlane(page,
								new TerrainTemplateConfiguration.LiquidPlaneConfiguration(el, evt.getNewValue()),
								terrainLoader));
						elevation.runAdjusting(() -> elevation.setSelectedValue(el));
					} else {
						// Hopefully the slider will be somewhere sensible
						// :)
						undoManager.storeAndExecute(new SetLiquidPlaneMaterial(page, evt.getNewValue(), terrainLoader));
					}
				}
			}
		});
		contentArea.addElement(new Label("Plane:", screen));
		contentArea.addElement(liquidPlane);
		contentArea.addElement(new Label("Elevation:", screen));

		elevation = new Spinner<Float>(screen, Orientation.HORIZONTAL, true);
		elevation.setInterval(10);
		elevation.setSpinnerModel(new FloatRangeSpinnerModel(-1000, 1000, 1, 0));
		elevation.setFormatterString("%1.0f");
		elevation.onChange(evt -> {

			if (!evt.getSource().isAdjusting() && playerTile != null) {
				TerrainInstance page = getPage(playerTile);
				if (page != null) {
					final TerrainTemplateConfiguration.LiquidPlaneConfiguration liquidPlaneConfiguration = page
							.getTerrainTemplate().getLiquidPlaneConfiguration();
					if (liquidPlaneConfiguration != null) {
						undoManager.storeAndExecute(new SetLiquidElevation(page, evt.getNewValue(), terrainLoader));
					}
				}
			}
		});
		contentArea.addElement(elevation);

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
		final TableRow selectedRow = heightBrushTexture.getSelectedRow();
		if (selectedRow != null) {
			LOG.info(String.format("Recreating brush for mode %s", mode));
			if (mode.equals(TerrainEditorMode.SMOOTH)) {
				heightBrush = new TerrainSmoothBrush(assetManager, undoManager, terrainLoader,
						sizeModel.getCurrentValue(), selectedRow.getValue().toString(), amountModel.getCurrentValue(),
						prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
								TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
										? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue()
										: Float.MIN_VALUE);
			} else {

				heightBrush = new TerrainHeightBrush(assetManager, undoManager, terrainLoader,
						sizeModel.getCurrentValue(), selectedRow.getValue().toString(),
						mode.equals(TerrainEditorMode.ERASE) ? -amountModel.getCurrentValue()
								: amountModel.getCurrentValue(),
						prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
								TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
										? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue()
										: Float.MIN_VALUE);
			}
		}
	}

	private void recreateFlattenBrush() {
		flattenBrush = new TerrainFlattenBrush(assetManager, undoManager, terrainLoader,
				flattenSizeModel.getCurrentValue(), flattenElevationModel.getCurrentValue(),
				"Textures/Brushes/Circle32.png", amountModel.getCurrentValue(),
				prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
						TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
								? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue()
								: Float.MIN_VALUE);
	}

	private void recreateSplatBrush() {
		final TableRow selectedRow = splatBrushTexture.getSelectedRow();
		if (selectedRow != null) {
			splatBrush = new TerrainSplatBrush(assetManager, undoManager, terrainLoader,
					splatSizeModel.getCurrentValue(), selectedRow.getValue().toString(),
					TerrainSplatBrush.Channel.values()[selectedTexture], splatRateModel.getCurrentValue(),
					prefs.getBoolean(TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE,
							TerrainConfig.TERRAIN_EDITOR_RESTRICT_BASELINE_DEFAULT)
									? ((Number) baseline.getSpinnerModel().getCurrentValue()).floatValue()
									: Float.MIN_VALUE);
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
			deleteTile.setEnabled(playerTile.isValid() && page != null && page.isHeightmapAvailable());
			newTile.setEnabled(playerTile.isValid() && ((page == null || !page.isHeightmapAvailable())));
			elevation.setEnabled(playerTile.isValid() && config != null && config.getMaterial() != null);
		} else {
			deleteTile.setEnabled(false);
			newTile.setEnabled(false);
			elevation.setEnabled(false);
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

		ModeTab(BaseScreen screen, TerrainEditorMode mode) {
			super(screen);
			this.mode = mode;
			// TerrainEditorAppState.this.mode = mode;
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
	abstract class AbstractTileOp implements UndoableCommand {

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
	class SetLiquidPlane implements UndoableCommand {

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
	class SetLiquidPlaneMaterial implements UndoableCommand {

		private final LiquidPlane newMaterial;
		private final TerrainInstance instance;
		private final TerrainLoader loader;
		private LiquidPlane current;
		private boolean wasNeedSave;

		SetLiquidPlaneMaterial(TerrainInstance instance, LiquidPlane newMaterial, TerrainLoader loader) {
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
	class SetLiquidElevation implements UndoableCommand {

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
