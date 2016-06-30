package org.iceterrain.maps;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.Icelib;
import org.icelib.PageLocation;
import org.icescene.HUDMessageAppState;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.iceterrain.TerrainAppState;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;
import org.iceterrain.TerrainLoader.Listener;
import org.iceui.HPosition;
import org.iceui.VPosition;
import org.iceui.controls.FancyPersistentWindow;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.SaveType;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;

import icemoon.iceloader.ServerAssetManager;
import icetone.controls.lists.ComboBox;
import icetone.controls.scrolling.ScrollPanel;
import icetone.controls.text.Label;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.ElementManager;
import icetone.core.layout.LUtil;
import icetone.core.layout.mig.MigLayout;
import icetone.listeners.MouseButtonListener;

/**
 * Stitches together terrain images for an overview
 */
public class TerrainMapAppState extends IcemoonAppState<IcemoonAppState<?>> implements Listener {
	final static Logger LOG = Logger.getLogger(TerrainMapAppState.class.getName());

	public enum MapType {
		COVERAGE, HEIGHTMAP, BASE, TEXTURE
	}

	private FancyPersistentWindow terrainMapWindow;
	private TerrainLoader terrainLoader;

	private ScrollPanel map;
	private ComboBox<MapType> type;

	public TerrainMapAppState(Preferences node) {
		super(node);
	}

	@Override
	protected IcemoonAppState<?> onInitialize(final AppStateManager stateManager, final IcesceneApp app) {
		return null;
	}

	@Override
	protected void postInitialize() {

		TerrainAppState tas = app.getStateManager().getState(TerrainAppState.class);
		terrainLoader = tas.getTerrainLoader();
		terrainLoader.addListener(this);

		// / Minmap window
		terrainMapWindow = new FancyPersistentWindow(screen, SceneConfig.LANDMARKS,
				screen.getStyle("Common").getInt("defaultWindowOffset"), VPosition.MIDDLE, HPosition.CENTER, new Vector2f(500, 500),
				FancyWindow.Size.SMALL, true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				stateManager.detach(TerrainMapAppState.this);
			}
		};
		terrainMapWindow.setWindowTitle("Terrain Overview");
		terrainMapWindow.setIsMovable(true);
		terrainMapWindow.setIsResizable(true);
		terrainMapWindow.setDestroyOnHide(true);

		map = new ScrollPanel(screen);
		map.setUseContentPaging(true);

		Container container = new Container(screen);
		container.setLayoutManager(new MigLayout(screen, "", "[][fill,grow]", "[]"));

		type = new ComboBox<MapType>(screen, MapType.values()) {
			@Override
			protected void onChange(int selectedIndex, MapType value) {
				reload();
			}
		};
		container.addChild(new Label("Type:", screen));
		container.addChild(type);

		// This
		final Element contentArea = terrainMapWindow.getContentArea();
		contentArea.setLayoutManager(new MigLayout(screen, "wrap 1", "[fill, grow]", "[shrink 0][fill, grow]"));

		contentArea.addChild(container);
		contentArea.addChild(map);

		// Show with an effect and sound
		screen.addElement(terrainMapWindow);

		reload();
	}

	protected void reload() {
		MapType type = (MapType) this.type.getSelectedListItem().getValue();

		// Find the bounds of the terrain
		map.getScrollableArea().removeAllChildren();
		if (terrainLoader.getDefaultTerrainTemplate() != null) {

			String path = terrainLoader.getDefaultTerrainTemplate().getAssetFolder();
			int minx = Integer.MAX_VALUE;
			int miny = Integer.MAX_VALUE;
			int maxx = Integer.MIN_VALUE;
			int maxy = Integer.MIN_VALUE;
			for (String n : ((ServerAssetManager) app.getAssetManager())
					.getAssetNamesMatching(String.format("%s/.*\\.cfg", path))) {
				String base = Icelib.getBasename(n);
				if (!base.startsWith("Terrain-")) {
					String[] a = base.split("_");
					String coords = a[a.length - 1];
					if (coords.startsWith("x")) {
						int x = Integer.parseInt(coords.substring(1, coords.indexOf('y')));
						int y = Integer.parseInt(coords.substring(coords.indexOf('y') + 1));
						minx = Math.min(minx, x);
						miny = Math.min(miny, y);
						maxx = Math.max(maxx, x);
						maxy = Math.max(maxy, y);
					}

				}
			}

			// map.getScrollableArea().removeAllChildren();
			// map.setScrollContentLayout(new GridLayout(maxx - minx + 1, maxy -
			// miny + 1));
			// map.layoutChildren();
			map.setScrollContentLayout(new MigLayout(screen, "gap 0, ins 0, wrap " + (maxx - minx + 1)));

			try {
				for (int y = miny; y <= maxy; y++) {
					for (int x = minx; x <= maxx; x++) {
						Future<TerrainInstance> tif = terrainLoader.load(new PageLocation(x, y));
						TerrainInstance ti = tif.get();
						String baseImgPath = null;
						switch (type) {
						case BASE:
							baseImgPath = String.format(ti.getTerrainTemplate().getHeightmapImageFormat(), x, y);
							break;
						case COVERAGE:
							baseImgPath = String.format(ti.getTerrainTemplate().getTextureCoverageFormat(), x, y);
							break;
						case HEIGHTMAP:
							baseImgPath = String.format(ti.getTerrainTemplate().getHeightmapImageFormat(), x, y);
							break;
						default:
							baseImgPath = String.format(ti.getTerrainTemplate().getTextureTextureFormat(), x, y);
							break;
						}
						String imgPath = String.format("%s/%s", ti.getTerrainTemplate().getAssetFolder(), baseImgPath);
						TileLabel tileEl = null;
						try {
							tileEl = new TileLabel(screen, imgPath, ti);
						} catch (AssetNotFoundException ane) {
							tileEl = new TileLabel(screen, ti);
						}
						map.addScrollableContent(tileEl);

					}
				}
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to load terrain map.", e);
				error("Failed to load terrain map.", e);
			}

			LOG.info(String.format("Terrain bounds: %d,%d -> %d,%d", minx, miny, maxx, maxy));
		}
		map.dirtyLayout(true);
		map.layoutChildren();

	}

	@Override
	protected void onCleanup() {
		terrainLoader.removeListener(this);
	}

	class TileLabel extends Label implements MouseButtonListener {
		private TerrainInstance ti;

		TileLabel(ElementManager screen, String imgPath, TerrainInstance ti) {
			super(screen, LUtil.LAYOUT_SIZE, Vector4f.ZERO, imgPath);
			init(ti);
		}

		TileLabel(ElementManager screen, TerrainInstance ti) {
			super(screen, LUtil.LAYOUT_SIZE);
			init(ti);
		}

		void init(TerrainInstance ti) {
			this.ti = ti;
			setText(String.format("%d,%d", ti.getPage().x, ti.getPage().y));
			setPreferredDimensions(new Vector2f(128, 128));
			setMinDimensions(new Vector2f(128, 128));
			setMaxDimensions(new Vector2f(128, 128));
			setIgnoreMouseButtons(false);
		}

		@Override
		public void onMouseLeftPressed(MouseButtonEvent evt) {
		}

		@Override
		public void onMouseLeftReleased(MouseButtonEvent evt) {
			Vector2f rel = new Vector2f((float) ti.getTerrainTemplate().getPageWorldX() / 2f,
					(float) ti.getTerrainTemplate().getPageWorldZ() / 2f);
			Vector2f wl = ti.relativeToWorld(rel);
			Vector3f newLocation = new Vector3f(wl.x, app.getCamera().getLocation().y, wl.y);
			HUDMessageAppState hud = app.getStateManager().getState(HUDMessageAppState.class);
			if (ti.getTerrainTemplate().getPage() != null) {
				hud.message(Level.INFO, String.format("Warping to %d,%d", ti.getTerrainTemplate().getPage().x,
						ti.getTerrainTemplate().getPage().y));
				app.getCamera().setLocation(newLocation);
			} else {
				hud.message(Level.SEVERE, String.format("No tile here."));
			}
		}

		@Override
		public void onMouseRightPressed(MouseButtonEvent evt) {
		}

		@Override
		public void onMouseRightReleased(MouseButtonEvent evt) {
		}
	}

	@Override
	public void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
			Quaternion initialRotation) {
		reload();
	}

	@Override
	public void terrainReload() {
	}

	@Override
	public void tileLoaded(TerrainInstance instance) {
	}

	@Override
	public void tileUnloaded(TerrainInstance instance) {
	}
}
