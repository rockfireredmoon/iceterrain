package org.iceterrain.app;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.icelib.AppInfo;
import org.icelib.PageLocation;
import org.icescene.Alarm;
import org.icescene.HUDMessageAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icescene.SceneConstants;
import org.icescene.assets.Assets;
import org.icescene.audio.AudioAppState;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.console.ConsoleAppState;
import org.icescene.environment.EnvironmentLight;
import org.icescene.environment.EnvironmentPhase;
import org.icescene.environment.PostProcessAppState;
import org.icescene.io.ModifierKeysAppState;
import org.icescene.io.MouseManager;
import org.icescene.options.OptionsAppState;
import org.icescene.props.EntityFactory;
import org.icescene.ui.WindowManagerAppState;
import org.iceskies.environment.EditableEnvironmentSwitcherAppState;
import org.icesterrain.landmarks.LandmarkAppState;
import org.icesterrain.landmarks.LandmarkEditorAppState;
import org.iceterrain.TerrainAppState;
import org.iceterrain.TerrainConfig;
import org.iceterrain.TerrainConstants;
import org.iceterrain.TerrainEditorAppState;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;
import org.iceui.IceUI;
import org.lwjgl.opengl.Display;

import com.jme3.bullet.BulletAppState;
import com.jme3.input.controls.ActionListener;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import icemoon.iceloader.ServerAssetManager;

public class Iceterrain extends IcesceneApp implements ActionListener {

	static {
		System.setProperty("iceloader.assetCache", System.getProperty("user.home") + File.separator + ".cache" + File.separator
				+ "iceterrain" + File.separator + "assets");
	}

	private final static String MAPPING_OPTIONS = "Options";
	private final static String MAPPING_LANDMARKS = "Landmarks";
	private final static String MAPPING_CONSOLE = "Console";

	private static final Logger LOG = Logger.getLogger(Iceterrain.class.getName());

	public static void main(String[] args) throws Exception {
		AppInfo.context = Iceterrain.class;

		// Parse command line
		Options opts = createOptions();
		Assets.addOptions(opts);

		CommandLine cmdLine = parseCommandLine(opts, args);

		// A single argument must be supplied, the URL (which is used to
		// determine router, which in turn locates simulator)
		if (cmdLine.getArgList().isEmpty()) {
			throw new Exception("No URL supplied.");
		}
		Iceterrain app = new Iceterrain(cmdLine);
		startApp(app, cmdLine, "PlanetForever - " + AppInfo.getName() + " - " + AppInfo.getVersion(),
				TerrainConstants.APPSETTINGS_NAME);
	}

	private TerrainLoader terrainLoader;
	private Vector3f lastLocation;
	private PageLocation lastViewTile;
	private Alarm.AlarmTask updateLocationPreferencesTimer;
	private Quaternion lastRotation;
	private Node weatherNode;

	private Iceterrain(CommandLine commandLine) {
		super(TerrainConfig.get(), commandLine, TerrainConstants.APPSETTINGS_NAME, "META-INF/TerrainAssets.cfg");
		setUseUI(true);
		setPauseOnLostFocus(false);
	}

	@Override
	public void restart() {
		Display.setResizable(true);
		super.restart();
	}

	@Override
	public void destroy() {
		terrainLoader.close();
		super.destroy();
		LOG.info("Destroyed application");
	}

	@Override
	public void onSimpleInitApp() {
		super.onSimpleInitApp();

		getCamera().setFrustumFar(SceneConstants.WORLD_FRUSTUM);

		EntityFactory propFactory = new EntityFactory(this, rootNode);

		/*
		 * The scene heirarchy is roughly :-
		 * 
		 * MainCamera      MapCamera
		 *     |              |
		 *    / \             |
		 *                   / \
		 * GameNode         
		 *     |\______ MappableNode
		 *     |              |\_________TerrainNode
		 *     |              \__________SceneryNode
		 *     |
		 *     \_______ WorldNode
		 *                  |\________ClutterNode
		 *                  \_________CreaturesNode
		 */

		flyCam.setMoveSpeed(prefs.getFloat(SceneConfig.BUILD_MOVE_SPEED, SceneConfig.BUILD_MOVE_SPEED_DEFAULT));
		flyCam.setRotationSpeed(prefs.getFloat(SceneConfig.BUILD_ROTATE_SPEED, SceneConfig.BUILD_ROTATE_SPEED_DEFAULT));
		flyCam.setZoomSpeed(prefs.getFloat(SceneConfig.BUILD_ZOOM_SPEED, SceneConfig.BUILD_ZOOM_SPEED_DEFAULT));
		flyCam.setDragToRotate(true);
		flyCam.setEnabled(true);
		setPauseOnLostFocus(false);

		// Scene
		Node gameNode = new Node("Game");
		Node mappableNode = new Node("Mappable");
		gameNode.attachChild(mappableNode);
		Node worldNode = new Node("World");
		gameNode.attachChild(worldNode);
		rootNode.attachChild(gameNode);

		// Environment needs audio (we can also set UI volume now)
		final AudioAppState audioAppState = new AudioAppState(prefs);
		stateManager.attach(audioAppState);
		screen.setUIAudioVolume(audioAppState.getActualUIVolume());

		// Some windows need management
		stateManager.attach(new WindowManagerAppState(prefs));

		// Need physics for terrain?
		stateManager.attach(new BulletAppState());

		// For error messages and stuff
		stateManager.attach(new HUDMessageAppState());

		// Mouse manager requires modifier keys to be monitored
		stateManager.attach(new ModifierKeysAppState());

		// Mouse manager for dealing with clicking, dragging etc.
		final MouseManager mouseManager = new MouseManager(rootNode, getAlarm());
		stateManager.attach(mouseManager);

		// Light
		EnvironmentLight el = new EnvironmentLight(cam, gameNode, prefs);

		// Need the post processor for pretty water
		stateManager.attach(new PostProcessAppState(prefs, el));

		// Terrain
		terrainLoader = new TerrainLoader(this, el, gameNode);
		terrainLoader.addListener(new TerrainLoader.Listener() {
			public void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
					Quaternion initialRotation) {

				if (templateConfiguration != null) {
					LOG.info(String.format("Warped to new terrain, setting location %s @ %6.3f,%6.3f,%6.3f",
							templateConfiguration.getBaseTemplateName(), initialLocation.x, initialLocation.y, initialLocation.z));

					lastLocation = initialLocation;
					if ((!terrainLoader.isReadOnly() && !TerrainEditorAppState.isEditing(stateManager))
							|| (!terrainLoader.isReadOnly() && TerrainEditorAppState.isEditing(stateManager))) {
						TerrainEditorAppState.toggle(stateManager);
					}

					// Set initial location
					TerrainAppState tas = stateManager.getState(TerrainAppState.class);
					cam.setLocation(initialLocation);
					if (initialRotation != null) {
						cam.setRotation(initialRotation);
					}
					if(tas.isInitialized()) {
						tas.playerViewLocationChanged(lastLocation);
						tas.playerTileChanged(getViewTile());
					}

				} else {

				}
			}

			public void terrainReload() {
			}

			public void tileLoaded(TerrainInstance instance) {
			}

			public void tileUnloaded(TerrainInstance instance) {
			}
		});

		// Skies
		EditableEnvironmentSwitcherAppState state = new EditableEnvironmentSwitcherAppState(null, prefs, null, el, gameNode,
				weatherNode);
		state.setPhase(EnvironmentPhase.DAY);
		stateManager.attach(state);

		TerrainAppState tas = new TerrainAppState(terrainLoader, prefs, el, mappableNode, propFactory, worldNode, mouseManager);
		tas.playerTileChanged(new PageLocation(1, 1));

		stateManager.attach(tas);

		// Landmarks allow devs to record points in the world and quickly warp
		// there
		stateManager.attach(new LandmarkAppState(prefs, terrainLoader));

		// A node that follows that camera, and is used to attach weather to
		weatherNode = new Node("Weather");
		gameNode.attachChild(weatherNode);

		// A menu
		stateManager.attach(new MenuAppState(terrainLoader, prefs, el, gameNode, weatherNode));

	}

	@Override
	public void registerAllInput() {
		super.registerAllInput();

		// Input
		getKeyMapManager().addMapping(MAPPING_OPTIONS);
		getKeyMapManager().addMapping(MAPPING_CONSOLE);
		getKeyMapManager().addMapping(MAPPING_LANDMARKS);
		getKeyMapManager().addListener(this, MAPPING_OPTIONS, MAPPING_CONSOLE, MAPPING_LANDMARKS);
	}

	@Override
	protected void configureAssetManager(ServerAssetManager serverAssetManager) {
		getAssets().setAssetsExternalLocation(
				System.getProperty("user.home") + File.separator + "Documents" + File.separator + "Iceterrain");
	}

	@Override
	protected void onUpdate(float tpf) {
		super.onUpdate(tpf);
		if (lastLocation == null || !cam.getLocation().equals(lastLocation)) {
			weatherNode.setLocalTranslation(cam.getLocation());
			lastLocation = cam.getLocation().clone();
			lastRotation = cam.getRotation().clone();
			if (updateLocationPreferencesTimer != null) {
				updateLocationPreferencesTimer.cancel();
			}
			updateLocationPreferencesTimer = getAlarm().timed(new Callable<Void>() {
				public Void call() throws Exception {
					TerrainTemplateConfiguration template = terrainLoader.getDefaultTerrainTemplate();
					if (template != null) {
						String templateName = template.getBaseTemplateName();
						Preferences node = prefs.node(templateName);
						node.putFloat("cameraLocationX", lastLocation.x);
						node.putFloat("cameraLocationY", lastLocation.y);
						node.putFloat("cameraLocationZ", lastLocation.z);
						node.putFloat("cameraRotationX", lastRotation.getX());
						node.putFloat("cameraRotationY", lastRotation.getY());
						node.putFloat("cameraRotationZ", lastRotation.getZ());
						node.putFloat("cameraRotationW", lastRotation.getW());
					}

					return null;
				}
			}, 5f);
			PageLocation viewTile = getViewTile();
			TerrainAppState tas = stateManager.getState(TerrainAppState.class);
			if (tas != null) {
				if (lastViewTile == null || !lastViewTile.equals(viewTile)) {
					lastViewTile = viewTile.clone();
					if (lastLocation != null) {
						tas.playerViewLocationChanged(lastLocation);
					}
					tas.playerTileChanged(lastViewTile);
				} else {
					tas.playerViewLocationChanged(lastLocation);
				}
			}
		}
	}

	private PageLocation getViewTile() {
		TerrainTemplateConfiguration template = terrainLoader.getTerrainTemplate();
		// if (template == null || terrainLoader.isGlobalTerrainTemplate()) {
		if (template == null) {
			return PageLocation.UNSET;
		} else {
			Vector3f loc = lastLocation;
			return loc == null ? PageLocation.UNSET : template.getTile(IceUI.toVector2fXZ(loc));
		}
	}

	@Override
	public void onAction(String name, boolean isPressed, float tpf) {
		if (getKeyMapManager().isMapped(name, MAPPING_OPTIONS)) {
			if (!isPressed) {
				final OptionsAppState state = stateManager.getState(OptionsAppState.class);
				if (state == null) {
					stateManager.attach(new OptionsAppState(prefs));
				} else {
					stateManager.detach(state);
				}
			}
		} else if (getKeyMapManager().isMapped(name, MAPPING_LANDMARKS)) {
			if (!isPressed) {
				final LandmarkEditorAppState state = stateManager.getState(LandmarkEditorAppState.class);
				if (state == null) {
					stateManager.attach(new LandmarkEditorAppState(prefs));
				} else {
					stateManager.detach(state);
				}
			}
		} else if (getKeyMapManager().isMapped(name, MAPPING_CONSOLE)) {
			if (!isPressed) {
				final ConsoleAppState state = stateManager.getState(ConsoleAppState.class);
				if (state == null) {
					ConsoleAppState console = new ConsoleAppState(prefs);
					stateManager.attach(console);
					console.show();
				} else {
					if (state.isVisible()) {
						state.hide();
					} else {
						state.show();
					}
				}
			}
		}
	}
}
