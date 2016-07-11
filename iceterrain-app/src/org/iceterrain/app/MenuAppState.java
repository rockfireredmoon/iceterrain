package org.iceterrain.app;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.icelib.Icelib;
import org.icelib.XDesktop;
import org.icelib.Zip;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConstants;
import org.icescene.ServiceRef;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.environment.EnvironmentLight;
import org.icescene.environment.EnvironmentPhase;
import org.icescene.help.HelpAppState;
import org.icescene.options.OptionsAppState;
import org.iceskies.environment.AbstractEnvironmentConfiguration;
import org.iceskies.environment.EditableEnvironmentSwitcherAppState;
import org.iceskies.environment.EnvironmentEditWindow;
import org.iceskies.environment.EnvironmentManager;
import org.iceskies.environment.EnvironmentSwitcherAppState;
import org.iceskies.environment.EnvironmentSwitcherAppState.EnvPriority;
import org.iceskies.environment.Environments;
import org.iceskies.environment.enhanced.EnhancedEnvironmentConfiguration;
import org.iceskies.environment.legacy.LegacyEnvironmentConfiguration;
import org.iceterrain.TerrainEditorAppState;
import org.iceterrain.TerrainInstance;
import org.iceterrain.TerrainLoader;
import org.iceterrain.TerrainLoader.Listener;
import org.iceterrain.maps.TerrainMapAppState;
import org.iceui.XFileSelector;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyInputBox;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.UIUtil;
import org.iceui.controls.XSeparator;
import org.iceui.controls.ZMenu;
import org.iceui.controls.ZMenu.ZMenuItem;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import icemoon.iceloader.IndexItem;
import icemoon.iceloader.ServerAssetManager;
import icetone.controls.extras.Indicator;
import icetone.controls.text.Label;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.Element.ZPriority;
import icetone.core.layout.mig.MigLayout;

public class MenuAppState extends IcemoonAppState<IcemoonAppState<?>> implements Listener {

	public interface OnCloneCallback {
		void run(TerrainTemplateConfiguration targetTemplate, FancyWindow input);
	}

	public enum CloneType {
		iceclient, official
	}

	public enum MenuActions {

		OPEN_TERRAIN_FOLDER
	}

	private static final Logger LOG = Logger.getLogger(MenuAppState.class.getName());
	private Container layer;
	private final TerrainLoader loader;
	private boolean cloning;
	private FancyButton terrain;
	private FancyButton options;
	private FancyButton exit;
	private File cloningTerrainDirFile;
	private Thread cloneThread;
	private FancyButton help;
	private FancyButton environment;
	private EnvironmentManager manager;
	private FancyButton map;
	private boolean loadingMenu;
	private boolean loadingEnvironmentMenu;

	@ServiceRef
	protected static Environments environments;

	public MenuAppState(TerrainLoader loader, Preferences prefs, EnvironmentLight environmentLight, Node gameNode,
			Node weatherNode) {
		super(prefs);
		this.loader = loader;
	}

	@Override
	protected void postInitialize() {
		manager = EnvironmentManager.get(assetManager);

		layer = new Container(screen);
		layer.setLayoutManager(new MigLayout(screen, "fill", "push[][][][][][]push", "[]push"));

		// Terrain
		terrain = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				createTerrainMenu(evt.getX(), evt.getY());
			}
		};
		terrain.setText("Terrain");
		layer.addChild(terrain);
		// Terrain
		environment = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				createEnvironmentMenu(evt.getX(), evt.getY());
			}
		};
		environment.setText("Environment");
		layer.addChild(environment);

		// Options
		map = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				toggleMap();
			}
		};
		map.setText("Map");
		layer.addChild(map);

		// Options
		options = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				toggleOptions();
			}
		};
		options.setText("Options");
		layer.addChild(options);

		// Help
		help = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				help();
			}
		};
		help.setText("Help");
		layer.addChild(help);

		// Exit
		exit = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				exitApp();
			}
		};
		exit.setText("Exit");
		layer.addChild(exit);

		//
		app.getLayers(ZPriority.MENU).addChild(layer);

		loader.addListener(this);
		setAvailable();
	}

	@Override
	protected void onCleanup() {
		app.getLayers(ZPriority.MENU).removeChild(layer);
		loader.removeListener(this);
	}

	private void help() {
		HelpAppState has = app.getStateManager().getState(HelpAppState.class);
		if (has == null) {
			app.getStateManager().attach(new HelpAppState(prefs));
		} else {
			app.getStateManager().detach(has);
		}
	}

	private void toggleMap() {
		TerrainMapAppState as = app.getStateManager().getState(TerrainMapAppState.class);
		if (as == null) {
			app.getStateManager().attach(new TerrainMapAppState(app.getPreferences()));
		} else {
			app.getStateManager().detach(as);
		}
	}

	private void toggleOptions() {
		final OptionsAppState state = stateManager.getState(OptionsAppState.class);
		if (state == null) {
			stateManager.attach(new OptionsAppState(prefs));
		} else {
			stateManager.detach(state);
		}
	}

	private void newEnvironment() {
		changeEnvironment("New Environment");

	}

	private void editEnvironment() {
		EnvironmentSwitcherAppState sas = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
		changeEnvironment("Edit Environment").setEnvironment(manager.getEnvironments(sas.getEnvironment()));
		// sas.setEdit(true);
	}

	private EnvironmentEditWindow changeEnvironment(String title) {
		return new EnvironmentEditWindow(title, screen)
		// {
		//
		// @Override
		// protected void onSave(String key, EnvironmentGroupConfiguration data)
		// {
		// File customEnvScript = ((IcesceneApp) app).getAssets()
		// .getExternalAssetFile(String.format("%s/%s", "Environment",
		// "Environment_Local.js"));
		//
		// try {
		// LocalEnvironments le = new LocalEnvironments(customEnvScript);
		// data.setKey(key);
		// le.env(data);
		// le.write();
		//
		// environments.remove(key);
		// environments.env(data);
		//
		// EditableEnvironmentSwitcherAppState eesa = app.getStateManager()
		// .getState(EditableEnvironmentSwitcherAppState.class);
		// if (eesa != null) {
		// eesa.reload();
		// }
		//
		// info(String.format("Saved local environment to %s",
		// customEnvScript));
		// } catch (Exception e) {
		// LOG.log(Level.SEVERE, "Failed to save local environment script.", e);
		// error("Failed to save local environment script.", e);
		// }
		//
		// }
		// }
		;
	}

	private ZMenu createEnvironmentMenu(float x, float y) {
		loadingEnvironmentMenu = true;
		setAvailable();

		final EnvironmentSwitcherAppState env = stateManager.getState(EnvironmentSwitcherAppState.class);
		ZMenu menu = new ZMenu(screen) {
			@Override
			public void onItemSelected(ZMenuItem item) {
				super.onItemSelected(item);
				if (Boolean.FALSE.equals(item.getValue())) {
					env.setEnvironment(EnvPriority.VIEWING, null);
				} else if (Boolean.TRUE.equals(item.getValue())) {
					editEnvironment();
				} else if (item.getValue() == String.class) {
					newEnvironment();
				}
			}

		};
		// Environments
		ZMenu environmentsMenu = new ZMenu(screen) {
			@Override
			public void onItemSelected(ZMenuItem item) {
				env.setEnvironment(EnvPriority.VIEWING, ((String) item.getValue()));
			}
		};
		menu.addMenuItem("Open", environmentsMenu, null);

		menu.addMenuItem("New Environment", null, String.class);
		if (env != null && env.getEnvironment() != null) {
			menu.addMenuItem("Edit", Boolean.TRUE);
			menu.addMenuItem("Close", Boolean.FALSE);
		}

		// Set current time of day
		ZMenu timeOfDay = new ZMenu(screen) {
			@Override
			public void onItemSelected(ZMenuItem item) {
				EnvironmentSwitcherAppState sas = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
				sas.setPhase((EnvironmentPhase) item.getValue());
			}
		};
		timeOfDay.addMenuItem(Icelib.toEnglish(EnvironmentPhase.SUNRISE), EnvironmentPhase.SUNRISE);
		timeOfDay.addMenuItem(Icelib.toEnglish(EnvironmentPhase.DAY), EnvironmentPhase.DAY);
		timeOfDay.addMenuItem(Icelib.toEnglish(EnvironmentPhase.SUNSET), EnvironmentPhase.SUNSET);
		timeOfDay.addMenuItem(Icelib.toEnglish(EnvironmentPhase.NIGHT), EnvironmentPhase.NIGHT);
		menu.addMenuItem("Set Time Of Day", timeOfDay, null);
		menu.addMenuItem(null, new XSeparator(screen, Element.Orientation.HORIZONTAL), null).setSelectable(false);
		// Environment configurations (that make up 'Environments')
		ZMenu configurationsMenu = new ZMenu(screen) {
			@Override
			public void onItemSelected(ZMenuItem item) {
				if (Boolean.FALSE.equals(item.getValue())) {
					if (env instanceof EditableEnvironmentSwitcherAppState
							&& ((EditableEnvironmentSwitcherAppState) env).isEdit())
						env.setEnvironment(EnvPriority.EDITING, null);
					else
						env.setEnvironment(EnvPriority.VIEWING, null);
				} else if (Boolean.TRUE.equals(item.getValue())) {
					editEnvironmentConfiguration();
				}
			}
		};

		// New environment configuration
		ZMenu newConfig = new ZMenu(screen) {
			@SuppressWarnings("unchecked")
			@Override
			public void onItemSelected(ZMenuItem item) {
				newEnvironmentConfiguration((Class<? extends AbstractEnvironmentConfiguration>) item.getValue());
			}
		};
		newConfig.addMenuItem("Enhanced", EnhancedEnvironmentConfiguration.class);
		newConfig.addMenuItem("Legacy", LegacyEnvironmentConfiguration.class);
		configurationsMenu.addMenuItem("New", newConfig, null).setSelectable(false);

		// Environments
		ZMenu openConfigurationMenu = new ZMenu(screen) {
			@Override
			public void onItemSelected(ZMenuItem item) {
				EnvironmentSwitcherAppState sas = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
				if (env instanceof EditableEnvironmentSwitcherAppState
						&& ((EditableEnvironmentSwitcherAppState) env).isEdit())
					sas.setEnvironment(EnvPriority.EDITING, ((String) item.getValue()));
				else
					sas.setEnvironment(EnvPriority.VIEWING, ((String) item.getValue()));
			}
		};
		configurationsMenu.addMenuItem("Open", openConfigurationMenu, null);
		if (env != null && env.getEnvironmentConfiguration() != null) {
			configurationsMenu.addMenuItem("Edit", Boolean.TRUE);
			configurationsMenu.addMenuItem("Close", Boolean.FALSE);
		}
		menu.addMenuItem("Configurations", configurationsMenu, null);

		app.getWorldLoaderExecutorService().execute(new Runnable() {

			@Override
			public String toString() {
				return "Loading available environments";
			}

			@Override
			public void run() {
				for (String k : manager.getEnvironments()) {
					app.enqueue(new Callable<Void>() {
						@Override
						public Void call() throws Exception {
							environmentsMenu.addMenuItem(k, k);
							return null;
						}
					});
				}

				for (String k : manager.getEnvironmentConfigurations()) {
					app.enqueue(new Callable<Void>() {
						@Override
						public Void call() throws Exception {
							openConfigurationMenu.addMenuItem(k, k);
							return null;
						}
					});
				}

				app.enqueue(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						loadingEnvironmentMenu = false;
						setAvailable();
						screen.addElement(menu);
						menu.showMenu(null, x, y);
						return null;
					}
				});
			}
		});

		return menu;
	}

	private void newEnvironmentConfiguration(final Class<? extends AbstractEnvironmentConfiguration> clazz) {

		final FancyInputBox dialog = new FancyInputBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				manager.newConfiguration(text, clazz);
				hideWindow();
				EditableEnvironmentSwitcherAppState sas = app.getStateManager()
						.getState(EditableEnvironmentSwitcherAppState.class);
				sas.setEnvironment(EnvPriority.VIEWING, text);
				sas.setEdit(true);
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.setWindowTitle("New Environment");
		dialog.setButtonOkText("Create");
		dialog.setMsg("");
		dialog.sizeToContent();
		dialog.setWidth(300);
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	private void editEnvironmentConfiguration() {
		EditableEnvironmentSwitcherAppState sas = app.getStateManager()
				.getState(EditableEnvironmentSwitcherAppState.class);
		String config = sas.getEnvironmentConfiguration();
		AbstractEnvironmentConfiguration envConfig = manager.getEnvironmentConfiguration(config);
		if (envConfig.isEditable()) {
			sas.setEdit(true);
		} else {
			error("This type of environment is not currently editable using the sky editor.");
		}
	}

	// private void selectEnvironment(ZMenuItem item) {
	// TerrainTemplateConfiguration terrainTemplate =
	// loader.getTerrainTemplate();
	// EnvironmentKey key;
	// if (item.getValue() instanceof EnvironmentKey) {
	// key = (EnvironmentKey) item.getValue();
	// } else {
	// EnvironmentConfiguration cfg = (EnvironmentConfiguration)
	// item.getValue();
	// key = cfg.getKey();
	// }
	// if (terrainTemplate == null ||
	// terrainTemplate.equals(loader.getGlobalTerrainTemplate())) {
	// app.getStateManager().getState(EnvironmentSwitcherAppState.class).setEnvironment(Priority.GLOBAL,
	// key);
	// } else if (loader.isReadOnly()) {
	// app.getStateManager().getState(EnvironmentSwitcherAppState.class).setEnvironment(Priority.USER,
	// key);
	// } else {
	// app.getStateManager().getState(EnvironmentSwitcherAppState.class).setEnvironment(Priority.DEFAULT_FOR_TERRAIN,
	// key);
	// terrainTemplate.setEnvironment(key.getEnvironmentName());
	// File cfgDir =
	// TerrainEditorAppState.getDirectoryForTerrainTemplate(app.getAssets(),
	// terrainTemplate);
	// try {
	// DOSWriter fos = new DOSWriter(new FileOutputStream(new File(cfgDir,
	// String.format("%s.nut",
	// terrainTemplate.getBaseTemplateName()))));
	// try {
	// fos.println(String.format("TerrainTemplate.setEnvironment(\"%s\");",
	// key.getEnvironmentName()));
	// } finally {
	// fos.close();
	// }
	// info(String.format("Set environment of %s to %s.",
	// terrainTemplate.getBaseTemplateName(), key.getEnvironmentName()));
	// } catch (Exception e) {
	// LOG.log(Level.SEVERE, "Failed to save environment script.", e);
	// error("Failed to save environment script.", e);
	// }
	// }
	// }

	private ZMenu createTerrainMenu(float x, float y) {
		loadingMenu = true;
		setAvailable();
		ZMenu menu = new ZMenu(screen) {
			@Override
			public void onItemSelected(ZMenu.ZMenuItem item) {
				super.onItemSelected(item);
				if (item.getValue().equals(MenuActions.OPEN_TERRAIN_FOLDER)) {
					final File terrainFolder = getTerrainFolder();
					try {
						XDesktop.getDesktop().open(terrainFolder);
					} catch (IOException ex) {
						LOG.log(Level.SEVERE, String.format("Failed to open terrain folder %s", terrainFolder), ex);
						error(String.format("Failed to open terrain folder %s", terrainFolder), ex);
					}
				} else if (item.getValue().equals(Boolean.TRUE)) {
					exportTerrainTemplate(loader.getDefaultTerrainTemplate());
				} else if (item.getValue().equals(Boolean.FALSE)) {

					if (loader.isNeedsSave()) {
						final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15),
								FancyWindow.Size.LARGE, true) {
							@Override
							public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
								hideWindow();
							}

							@Override
							public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
								hideWindow();
								closeEditor();
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
					} else {
						closeEditor();
					}
				} else {
					validateTerrainTemplate(((TerrainTemplateConfiguration) item.getValue()));
				}
			}
		};
		if (loader.getDefaultTerrainTemplate() != null) {
			menu.addMenuItem(null, new XSeparator(screen, Element.Orientation.HORIZONTAL), null).setSelectable(false);
			menu.addMenuItem("Close", Boolean.FALSE);
			menu.addMenuItem("Export", Boolean.TRUE);
		}
		for (MenuActions n : MenuActions.values()) {
			menu.addMenuItem(Icelib.toEnglish(n), n);
		}
		menu.addMenuItem(null, new XSeparator(screen, Element.Orientation.HORIZONTAL), null).setSelectable(false);
		app.getWorldLoaderExecutorService().execute(new Runnable() {

			@Override
			public String toString() {
				return "Loading available terrain";
			}

			@Override
			public void run() {
				for (String n : ((ServerAssetManager) app.getAssetManager())
						.getAssetNamesMatching(".*/Terrain-[a-zA-Z\\d[_]]*\\.cfg")) {
					TerrainTemplateConfiguration cfg = TerrainTemplateConfiguration.get(assetManager, n);
					if (!cfg.getBaseTemplateName().equals("Common") && !cfg.getBaseTemplateName().equals("Default")
							&& !cfg.equals(loader.getTerrainTemplate())) {
						app.enqueue(new Callable<Void>() {

							@Override
							public Void call() throws Exception {
								ZMenuItem m = menu.addMenuItem(Icelib.toEnglish(cfg.getBaseTemplateName()), cfg);
								if (((IcesceneApp) app).getAssets().isExternal(cfg.getAssetPath())) {
									m.getItemTextElement().setFontColor(ColorRGBA.Green);
								}
								menu.sizeToContent();
								return null;
							}
						});
					}
				}
				app.enqueue(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						loadingMenu = false;
						setAvailable();
						screen.addElement(menu);
						menu.showMenu(null, x, y);
						return null;
					}
				});
			}
		});

		// Show menu
		return menu;
	}

	private void closeEditor() {
		loader.setTerrainTemplate(null);
		if (TerrainEditorAppState.isEditing(stateManager)) {
			TerrainEditorAppState.toggle(stateManager);
		}
	}

	private File getTerrainFolder() {
		return new File(((IcesceneApp) app).getAssets().getExternalAssetsFolder(), SceneConstants.TERRAIN_PATH);
	}

	private void askForCloneName(final TerrainTemplateConfiguration sourceTemplate) {
		final FancyInputBox dialog = new FancyInputBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				// Check the name doesn't already exist
				File f = MenuAppState.this.app.getAssets()
						.getExternalAssetFile(String.format("%s/%s", SceneConstants.TERRAIN_PATH, text));
				if (f.exists()) {
					error(String.format("The template %s already exists locally."));
					hideWindow();
				} else {
					// Close existing terrain
					closeEditor();

					// Clone#
					hideWindow();

					String terrainDir = sourceTemplate.getAssetFolder();
					String terrainName = text.replace(" ", "").replace("/", "").replace("\\", "");
					terrainDir = Icelib.getDirname(terrainDir) + "/Terrain-" + terrainName;
					LOG.info(String.format("New terrain directory will be %s", terrainDir));
					cloneTerrainTemplate(CloneType.iceclient, sourceTemplate, terrainName, terrainDir,
							MenuAppState.this.app.getAssets().getExternalAssetsFolder(), new OnCloneCallback() {

								@Override
								public void run(TerrainTemplateConfiguration targetTemplate, FancyWindow input) {
									setAvailable();
									input.hideWindow();
									loadTerrainTemplate(targetTemplate, true);

								}
							});
				}
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.setWindowTitle("New Clone");
		dialog.setButtonOkText("Clone");
		dialog.setMsg(sourceTemplate.getBaseTemplateName() + " Copy");
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		dialog.sizeToContent();
		dialog.setWidth(300);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	private void exportTerrainTemplate(final TerrainTemplateConfiguration template) {
		final FancyInputBox dialog = new FancyInputBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				final String terrainDir = FilenameUtils.getFullPathNoEndSeparator(template.getAssetFolder());
				File terrainDirFile = new File(MenuAppState.this.app.getAssets().getExternalAssetsFolder(),
						terrainDir.replace('/', File.separatorChar));
				File zipFile = new File(terrainDirFile, "Terrain-" + text + ".zip");
				XFileSelector sel = XFileSelector.create(terrainDirFile.getAbsolutePath());
				sel.setFileSelectionMode(XFileSelector.FILES_ONLY);
				sel.setSelectedFile(zipFile);
				if (sel.showDialog(null, "Choose Archive File") == XFileSelector.APPROVE_OPTION) {
					File outDir = new File(new File(System.getProperty("java.io.tmpdir")),
							System.getProperty("user.name") + ".terexp");
					LOG.info(String.format("New terrain directory will be %s", outDir));
					cloneTerrainTemplate(CloneType.official, template,
							text.replace(" ", "").replace("/", "").replace("\\", ""), "Terrain-" + text, outDir,
							new OnCloneCallback() {

								@Override
								public void run(TerrainTemplateConfiguration targetTemplate, FancyWindow input) {
									setAvailable();
									input.hideWindow();
									try {
										Zip.compress(outDir, sel.getSelectedFile());
									} catch (Exception e) {
										LOG.log(Level.SEVERE, "Failed to export.", e);
										error("Failed to export.", e);
									}
									clearUpClonedDirectory();
								}
							});

				}
				;
				hideWindow();
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.setWindowTitle("Export Terrain");
		dialog.setButtonOkText("Export");
		dialog.setMsg(template.getBaseTemplateName());
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		dialog.sizeToContent();
		dialog.setWidth(300);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	private void validateTerrainTemplate(final TerrainTemplateConfiguration template) {

		// Determine if this is the terrain is available locally, if not, is
		// provided by
		// the server and so must be cloned to a local copy first

		File localTerrainFolder = new File(app.getAssets().getExternalAssetsFolder(),
				template.getAssetFolder().replace('/', File.separatorChar));
		if (!localTerrainFolder.exists()) {
			LOG.info(String.format("Local, terrain folder %s does not exist, offering to clone",
					localTerrainFolder.getAbsolutePath()));

			final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE,
					true) {
				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hideWindow();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					// Clone#
					hideWindow();
					askForCloneName(template);
				}

				@Override
				public void createButtons(Element buttons) {
					FancyButton btnReadOnly = new FancyButton(screen, getUID() + ":btnReadOnly") {
						@Override
						public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
							loadTerrainTemplate(template, false);
							hideWindow();
						}
					};
					btnReadOnly.setText("Read-Only");
					buttons.addChild(btnReadOnly);
					form.addFormElement(btnReadOnly);
					super.createButtons(buttons);
				}
			};
			dialog.setDestroyOnHide(true);
			dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
			dialog.setWindowTitle("Read-Only Template");
			dialog.setButtonOkText("Clone");
			dialog.setMsg(String.format("The template %s is provided by the server, so cannot be "
					+ "directly edited. You may either CLONE the terrain for local editing, or "
					+ "open the template in read-only mode.", template.getBaseTemplateName()));
			dialog.setIsResizable(false);
			dialog.setIsMovable(false);
			dialog.sizeToContent();
			UIUtil.center(screen, dialog);
			screen.addElement(dialog, null, true);
			dialog.showAsModal(true);
		} else {
			LOG.info(String.format("Local terrain folder %s exists, opening", localTerrainFolder.getAbsolutePath()));
			loadTerrainTemplate(template, true);
		}
	}

	private void cloneTerrainTemplate(final CloneType cloneType, final TerrainTemplateConfiguration sourceTemplate,
			final String newBaseTemplateName, final String terrainDir, final File targetDir, OnCloneCallback whenDone) {
		final String baseSourceTemplateName = sourceTemplate.getBaseTemplateName();
		final TerrainTemplateConfiguration targetTemplate = sourceTemplate.clone();
		targetTemplate.setBaseTemplateName(newBaseTemplateName);
		cloningTerrainDirFile = new File(targetDir, terrainDir.replace("/", File.separator));

		String assetPattern = String.format("%s/.*", sourceTemplate.getAssetFolder());
		final Set<IndexItem> assetsMatching = ((ServerAssetManager) app.getAssetManager())
				.getAssetsMatching(assetPattern);

		/*
		 * Some template folder have multiple terrains in them. Regexp patterns
		 * are used to validate
		 */
		String configPattern = sourceTemplate.getPerPageConfig().replace("%d", "\\d+");
		String basePattern = sourceTemplate.getTextureBaseFormat().replace("%d", "\\d+");
		String coveragePattern = sourceTemplate.getTextureCoverageFormat().replace("%d", "\\d+");
		String heightmapPattern = sourceTemplate.getHeightmapImageFormat().replace("%d", "\\d+");
		String[] patterns = new String[] { configPattern, basePattern, coveragePattern, heightmapPattern };

		// We don't want the main configuration file, we will be writing our own
		// cloned version
		boolean matches = false;
		for (Iterator<IndexItem> it = assetsMatching.iterator(); it.hasNext();) {
			IndexItem i = it.next();
			matches = false;
			for (String p : patterns) {
				if (FilenameUtils.getName(i.getName()).matches(p)) {
					matches = true;
					break;
				}
			}
			if (!matches) {
				it.remove();
			}

		}

		long totalSize = 0;
		for (IndexItem it : assetsMatching) {
			long size = it.getSize();
			totalSize += size;
		}

		if (totalSize == 0) {
			error("There is nothing to clone. I am confused :\\");
			return;
		}

		/*
		 * If the size is less than zero, then the indexer does not know them.
		 * So we use total number of files instead
		 */
		// final boolean useFileCount = totalSize < 0;

		/*
		 * TODO For now, always use file count. It's better than the innaccurate
		 * results when downloading encrypted assets from the server (the index
		 * size is not the actual size).
		 */
		final boolean useFileCount = true;

		final long fTotalSize = totalSize;

		LOG.info(String.format("Will clone %d bytes of terrain template %s", totalSize, targetTemplate.getAssetPath()));
		final Label progressTitle = new Label("Cloneing ..", screen);
		final FancyWindow w = new FancyWindow(screen, Vector2f.ZERO, FancyWindow.Size.LARGE, false);
		w.getContentArea().setLayoutManager(new MigLayout(screen, "fill, wrap 1", "[]", "[][]"));
		w.setIsResizable(false);
		w.setIsMovable(false);
		w.setWindowTitle("Cloneing");
		w.getContentArea().addChild(progressTitle, "growx, wrap");
		final Indicator overallProgress = new Indicator(screen, Element.Orientation.HORIZONTAL);
		overallProgress.setMaxValue(useFileCount ? assetsMatching.size() : 100);
		overallProgress.setCurrentValue(0);
		w.setDestroyOnHide(true);
		w.getContentArea().addChild(overallProgress, "shrink 0, growx, wrap");
		w.sizeToContent();
		w.setWidth(200);
		UIUtil.center(screen, w);
		screen.addElement(w);

		// TODO the progress totals aren't quite right, i think because of
		// differences
		// between the encrypted and the unencrypted size of the asset (maybe
		// the index
		// needs to carry both)

		// Get off the scene thread for the actual copying
		cloning = true;
		setAvailable();

		cloneThread = new Thread("Clone" + sourceTemplate.getAssetPath()) {
			@Override
			public void run() {
				final ThreadLocal<Long> total = new ThreadLocal<Long>();
				total.set(0l);
				try {
					int index = 0;
					for (IndexItem i : assetsMatching) {
						if (useFileCount) {
							final int fi = ++index;
							app.enqueue(new Callable<Void>() {
								public Void call() throws Exception {
									overallProgress.setCurrentValue(fi);
									return null;
								}
							});
						}
						LOG.info("Cloning " + i.getName());
						final AssetInfo inf = app.getAssetManager().locateAsset(new AssetKey<String>(i.getName()));
						app.enqueue(new Callable<Void>() {
							public Void call() throws Exception {
								progressTitle.setText(Icelib.getFilename(inf.getKey().getName()));
								return null;
							}
						});
						InputStream in = inf.openStream();
						try {
							String name = i.getName();

							// Determine the new name
							// TODO not sure if this is sufficient until i see
							// more terrain files
							String basename = Icelib.getFilename(name);
							if (basename.startsWith(baseSourceTemplateName + "_Base_")) {
								String coordsAndExt = basename.substring(baseSourceTemplateName.length() + 6);
								if (cloneType == CloneType.official)
									name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
											+ newBaseTemplateName + "_" + FilenameUtils.getBaseName(coordsAndExt) + "/"
											+ newBaseTemplateName + "_Base_" + coordsAndExt;
								else
									name = terrainDir + "/" + newBaseTemplateName + "_Base_" + coordsAndExt;
							} else if (basename.startsWith(baseSourceTemplateName + "_Coverage_")) {

								String coordsAndExt = basename.substring(baseSourceTemplateName.length() + 10);
								if (cloneType == CloneType.official)
									name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
											+ newBaseTemplateName + "_" + FilenameUtils.getBaseName(coordsAndExt) + "/"
											+ newBaseTemplateName + "_Coverage_" + coordsAndExt;
								else
									name = terrainDir + "/" + newBaseTemplateName + "_Coverage_" + coordsAndExt;
							} else if (basename.startsWith(baseSourceTemplateName + "_Height_")) {
								String coordsAndExt = basename.substring(baseSourceTemplateName.length() + 8);
								if (cloneType == CloneType.official)
									name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
											+ newBaseTemplateName + "_" + FilenameUtils.getBaseName(coordsAndExt) + "/"
											+ newBaseTemplateName + "_Height_" + coordsAndExt;
								else
									name = terrainDir + "/" + newBaseTemplateName + "_Height_" + coordsAndExt;
							} else if (basename.startsWith(baseSourceTemplateName + "_Texture_")) {
								String coordsAndExt = basename.substring(baseSourceTemplateName.length() + 9);
								if (cloneType == CloneType.official)
									name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
											+ newBaseTemplateName + "_" + FilenameUtils.getBaseName(coordsAndExt) + "/"
											+ newBaseTemplateName + "_Texture_" + coordsAndExt;
								else
									name = terrainDir + "/" + newBaseTemplateName + "_Texture_" + coordsAndExt;
							} else if (basename.equals("Terrain-" + baseSourceTemplateName + ".nut")) {
								if (cloneType == CloneType.official)
									name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
											+ newBaseTemplateName + "/Terrain-" + newBaseTemplateName + ".nut";
								else
									name = terrainDir + "/Terrain-" + newBaseTemplateName + ".nut";
							} else {
								String coordsAndExt = basename.substring(baseSourceTemplateName.length() + 1);
								if (basename.startsWith(baseSourceTemplateName + "_") && basename.endsWith(".nut")) {
									if (cloneType == CloneType.official)
										name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
												+ newBaseTemplateName + "_" + FilenameUtils.getBaseName(coordsAndExt)
												+ "/" + newBaseTemplateName + "_" + coordsAndExt;
									else
										name = terrainDir + "/" + newBaseTemplateName + "_" + coordsAndExt;
								} else if (basename.startsWith(baseSourceTemplateName + "_")) {
									if (cloneType == CloneType.official)
										name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
												+ newBaseTemplateName + "_" + FilenameUtils.getBaseName(coordsAndExt)
												+ "/" + newBaseTemplateName + "_" + coordsAndExt;
									else
										name = terrainDir + "/" + newBaseTemplateName + "_" + coordsAndExt;
								} else {
									LOG.warning(
											String.format("Unsure how to rename file %s in %s", basename, terrainDir));
									if (cloneType == CloneType.official)
										name = FilenameUtils.getFullPathNoEndSeparator(terrainDir) + "/Terrain-"
												+ newBaseTemplateName + "/" + basename;
									else
										name = terrainDir + "/" + basename;
								}
							}

							File outputFile = Icelib.makeParent(app.getAssets().getExternalAssetFile(name, targetDir));
							OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile), 65536);
							if (!useFileCount) {
								out = new FilterOutputStream(out) {

									private long lastUpdate = -1;

									@Override
									public void write(int b) throws IOException {
										super.write(b);
										total.set(total.get() + 1);
										updateProgress();
									}

									@Override
									public void write(byte[] b, int off, int len) throws IOException {
										super.write(b, off, len);
										total.set(total.get() + len);
										updateProgress();
									}

									@Override
									public void close() throws IOException {
										super.close();
										showUpdate(overallProgress, total);
										;
									}

									private void updateProgress() throws IOException {
										if (Thread.interrupted()) {
											throw new IOException(new InterruptedException());
										}
										long now = System.currentTimeMillis();
										if (lastUpdate == -1 || now > lastUpdate + 100) {
											lastUpdate = now;
											showUpdate(overallProgress, total);
										}
									}

									private void showUpdate(final Indicator overallProgress,
											final ThreadLocal<Long> total) {
										final long fTot = total.get();
										app.enqueue(new Callable<Void>() {
											public Void call() throws Exception {
												double d = (double) fTot / (double) fTotalSize;
												float pc = (float) d * 100f;
												overallProgress.setCurrentValue(pc);
												return null;
											}
										});
									}
								};
							}
							try {
								IOUtils.copy(in, out);
							} finally {
								out.close();
							}
						} finally {
							in.close();
						}
					}

					// Write the main configuration file
					FileOutputStream out = new FileOutputStream(app.getAssets()
							.getExternalAssetFile(terrainDir + "/Terrain-" + newBaseTemplateName + ".cfg", targetDir));
					try {
						targetTemplate.write(out, false);
					} finally {
						out.close();
					}

					// Re-index
					((ServerAssetManager) app.getAssetManager()).index();

					cloning = false;
					if (whenDone != null) {
						app.enqueue(new Callable<Void>() {

							@Override
							public Void call() throws Exception {
								whenDone.run(targetTemplate, w);
								return null;
							}
						});
					}
				} catch (Exception e) {
					cloning = false;
					if (e.getCause() instanceof InterruptedException) {
						LOG.warning("Interrupted.");
					} else {
						error("Failed to clone terrain.", e);
						LOG.log(Level.SEVERE, "Failed to clone.", e);
					}
					clearUpClonedDirectory();
					app.enqueue(new Callable<Void>() {
						public Void call() throws Exception {
							setAvailable();
							w.hideWindow();
							return null;
						}
					});
				} finally {
				}
			}
		};
		cloneThread.setPriority(Thread.MIN_PRIORITY);
		cloneThread.start();

	}

	private void setAvailable() {

		environment.setIsEnabled(!cloning && !loadingMenu && !loadingEnvironmentMenu);
		terrain.setIsEnabled(!cloning && !loadingMenu && !loadingEnvironmentMenu);
		options.setIsEnabled(!cloning);
		map.setIsEnabled(
				!cloning && !loadingMenu && !loadingEnvironmentMenu && loader.getDefaultTerrainTemplate() != null);
	}

	private void exitApp() {
		if (loader.isNeedsSave()) {
			final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE,
					true) {
				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hideWindow();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					app.stop();
				}
			};
			dialog.setDestroyOnHide(true);
			dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
			dialog.setWindowTitle("Confirm Exit");
			dialog.setButtonOkText("Exit");
			dialog.setMsg("You have unsaved edits! Are you sure you wish to exit?");

			dialog.setIsResizable(false);
			dialog.setIsMovable(false);
			dialog.sizeToContent();
			UIUtil.center(screen, dialog);
			screen.addElement(dialog, null, true);
			dialog.showAsModal(true);
		} else {
			if (cloning) {
				LOG.info("Interrupting cloneing");
				cloneThread.interrupt();
				LOG.info("Interrupted cloneing");
			}
			app.stop();
		}
	}

	private void loadTerrainTemplate(final TerrainTemplateConfiguration template, boolean writeable) {

		// Find the lowest number tile in the terrain to be the default start
		// position
		final Set<String> assetsMatching = ((ServerAssetManager) app.getAssetManager())
				.getAssetNamesMatching(String.format(".*/Terrain-%1$s/%2$s_Height_x.*",
						template.getTerrainTemplateGroup(), template.getBaseTemplateName()));
		int minX = 0;
		int minY = 0;
		int minVal = Integer.MAX_VALUE;
		for (String n : assetsMatching) {
			int idx = n.lastIndexOf('/');
			n = n.substring(idx + 1);
			// idx = n.indexOf('_', n.indexOf('_') + 1);
			idx = n.lastIndexOf('_');
			n = n.substring(idx + 1);
			idx = n.indexOf('y');
			int x = Integer.parseInt(n.substring(1, idx));
			int y = Integer.parseInt(n.substring(idx + 1, n.indexOf('.')));
			int val = x + (y * 1000);
			if (val < minVal) {
				minX = x;
				minY = y;
				minVal = val;
			}
		}
		Vector3f defaultLocation = new Vector3f(template.getPageWorldX() * minX, 36.0f,
				template.getPageWorldZ() * minY);
		LOG.info("Default start position for this is " + minX + "," + minY + " ( " + defaultLocation + ")");

		// See if there is a last know position for this template
		Preferences node = prefs.node(template.getBaseTemplateName());
		Quaternion defaultRotation = new Quaternion();
		defaultRotation.fromAngles(0, FastMath.HALF_PI, 0);
		Vector3f position = new Vector3f(node.getFloat("cameraLocationX", defaultLocation.x),
				node.getFloat("cameraLocationY", defaultLocation.y),
				node.getFloat("cameraLocationZ", defaultLocation.z));
		LOG.info(String.format("Chosen start position for the camera is %s", position));
		defaultRotation = new Quaternion(node.getFloat("cameraRotationX", defaultRotation.getX()),
				node.getFloat("cameraRotationY", defaultRotation.getY()),
				node.getFloat("cameraRotationZ", defaultRotation.getZ()),
				node.getFloat("cameraRotationW", defaultRotation.getW()));
		// app.getCamera().setLocation(
		// position);
		// app.getCamera().setRotation(
		// defaultRotation);

		// Setting the template fires an event which is picked up in the
		// application, which
		// activates the editor if needed
		loader.setReadOnly(!writeable);
		loader.setDefaultTerrainTemplate(template, position, defaultRotation);

	}

	protected void clearUpClonedDirectory() {
		if (cloningTerrainDirFile != null) {
			try {
				LOG.info(String.format("Clearing up partially cloned directory %s", cloningTerrainDirFile));
				FileUtils.deleteDirectory(cloningTerrainDirFile);
			} catch (IOException ex) {
				LOG.log(Level.SEVERE,
						String.format("Failed to clearing up partially cloned directory.%", cloningTerrainDirFile));
			} finally {
				cloningTerrainDirFile = null;
			}

		}
	}

	@Override
	public void templateChanged(TerrainTemplateConfiguration templateConfiguration, Vector3f initialLocation,
			Quaternion initialRotation) {
		setAvailable();
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
