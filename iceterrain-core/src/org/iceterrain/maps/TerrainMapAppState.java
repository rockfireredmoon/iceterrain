package org.iceterrain.maps;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.Icelib;
import org.icelib.PageLocation;
import org.icescene.HUDMessageAppState;
import org.icescene.HUDMessageAppState.Channel;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.iceterrain.TerrainAppState;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;
import org.iceterrain.TerrainLoader.Listener;

import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont.Align;
import com.jme3.font.BitmapFont.VAlign;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.texture.Texture;

import icemoon.iceloader.ServerAssetManager;
import icetone.controls.lists.ComboBox;
import icetone.controls.scrolling.ScrollPanel;
import icetone.controls.text.Label;
import icetone.core.BaseElement;
import icetone.core.BaseScreen;
import icetone.core.Size;
import icetone.core.StyledContainer;
import icetone.core.layout.mig.MigLayout;
import icetone.extras.windows.PersistentWindow;
import icetone.extras.windows.SaveType;

/**
 * Stitches together terrain images for an overview
 */
public class TerrainMapAppState extends IcemoonAppState<IcemoonAppState<?>> implements Listener {
	final static Logger LOG = Logger.getLogger(TerrainMapAppState.class.getName());

	public enum MapType {
		COVERAGE, HEIGHTMAP, BASE, TEXTURE
	}

	private PersistentWindow terrainMapWindow;
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
		terrainMapWindow = new PersistentWindow(screen, SceneConfig.LANDMARKS, VAlign.Center, Align.Center,
				new Size(500, 500), true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				stateManager.detach(TerrainMapAppState.this);
			}
		};
		terrainMapWindow.setWindowTitle("Terrain Overview");
		terrainMapWindow.setMovable(true);
		terrainMapWindow.setResizable(true);
		terrainMapWindow.setDestroyOnHide(true);

		map = new ScrollPanel(screen);

		StyledContainer container = new StyledContainer(screen);
		container.setLayoutManager(new MigLayout(screen, "", "[][fill,grow]", "[]"));

		type = new ComboBox<MapType>(screen, MapType.values());
		type.onChange(evt -> app.getWorldLoaderExecutorService().execute(() -> reload()));
		container.addElement(new Label("Type:", screen));
		container.addElement(type);

		// This
		final BaseElement contentArea = terrainMapWindow.getContentArea();
		contentArea.setLayoutManager(new MigLayout(screen, "wrap 1", "[fill, grow]", "[shrink 0][fill, grow]"));

		contentArea.addElement(container);
		contentArea.addElement(map);

		// Show with an effect and sound
		screen.addElement(terrainMapWindow);

		app.getWorldLoaderExecutorService().execute(() -> reload());
	}

	protected void reload() {
		MapType type = (MapType) this.type.getSelectedListItem().getValue();

		// Find the bounds of the terrain
		app.enqueue(() -> map.getScrollableArea().removeAllChildren());
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
			map.setScrollContentLayout(new MigLayout(screen, "gap 0, ins 0"));

			LOG.info(String.format("Mapping %d,%d -> %d,%d (%d,%d)", minx, miny, maxx, maxy, maxx - minx, maxy - miny));

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
						final Object cons = x == maxx ? "wrap" : null;
						String imgPath = String.format("%s/%s", ti.getTerrainTemplate().getAssetFolder(), baseImgPath);
						try {
							final Texture tex = app.getAssetManager().loadTexture(imgPath);
							app.enqueue(() -> {
								map.addScrollableContent(new TileLabel(screen, ti).setTexture(tex), cons);
								return null;
							});
						} catch (AssetNotFoundException ane) {
							app.enqueue(() -> {
								map.addScrollableContent(new TileLabel(screen, ti), cons);
								return null;
							});
						}

					}
				}
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to load terrain map.", e);
				error("Failed to load terrain map.", e);
			}

			LOG.info(String.format("Terrain bounds: %d,%d -> %d,%d", minx, miny, maxx, maxy));
		}
		// app.enqueue(() -> {
		// map.dirtyLayout(false, LayoutType.boundsChange());
		// map.layoutChildren();
		// });

	}

	@Override
	protected void onCleanup() {
		terrainLoader.removeListener(this);
	}

	class TileLabel extends Label {

		TileLabel(BaseScreen screen, String imgPath, TerrainInstance ti) {
			super(screen);
			setTexture(imgPath);
			init(ti);
		}

		TileLabel(BaseScreen screen, TerrainInstance ti) {
			super(screen);
			init(ti);
		}

		void init(TerrainInstance ti) {
			setText(String.format("%d,%d", ti.getPage().x, ti.getPage().y));
			setPreferredDimensions(new Size(128, 128));
			setMinDimensions(new Size(128, 128));
			setMaxDimensions(new Size(128, 128));
			setIgnoreMouseButtons(false);
			onMouseReleased(evt -> {
				Vector2f rel = new Vector2f((float) ti.getTerrainTemplate().getPageWorldX() / 2f,
						(float) ti.getTerrainTemplate().getPageWorldZ() / 2f);
				Vector2f wl = ti.relativeToWorld(rel);
				Vector3f newLocation = new Vector3f(wl.x, app.getCamera().getLocation().y, wl.y);
				HUDMessageAppState hud = app.getStateManager().getState(HUDMessageAppState.class);
				if (ti.getTerrainTemplate().getPage() != null) {
					hud.message(Channel.INFORMATION, String.format("Warping to %d,%d",
							ti.getTerrainTemplate().getPage().x, ti.getTerrainTemplate().getPage().y));
					app.getCamera().setLocation(newLocation);
				} else {
					hud.message(Channel.ERROR, String.format("No tile here."));
				}
			});
		}

	}

	@Override
	public void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
			Quaternion initialRotation) {
		app.getWorldLoaderExecutorService().execute(() -> reload());
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
