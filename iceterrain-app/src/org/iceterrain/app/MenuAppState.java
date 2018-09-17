package org.iceterrain.app;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.icelib.AppInfo;
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
import org.iceui.actions.ActionAppState;
import org.iceui.actions.ActionMenu;
import org.iceui.actions.ActionMenuBar;
import org.iceui.actions.AppAction;
import org.iceui.controls.ElementStyle;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import icemoon.iceloader.IndexItem;
import icemoon.iceloader.ServerAssetManager;
import icetone.controls.buttons.PushButton;
import icetone.controls.containers.Frame;
import icetone.controls.extras.Indicator;
import icetone.controls.text.Label;
import icetone.core.BaseElement;
import icetone.core.Orientation;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.mig.MigLayout;
import icetone.extras.windows.AlertBox;
import icetone.extras.windows.DialogBox;
import icetone.extras.windows.InputBox;

public class MenuAppState extends IcemoonAppState<IcemoonAppState<?>>
		implements Listener, org.iceskies.environment.EnvironmentSwitcherAppState.Listener {

	public enum CloneType {
		official, iceclient
	}

	public interface OnCloneCallback {
		void run(TerrainTemplateConfiguration targetTemplate, Frame input);
	}

	private static final Logger LOG = Logger.getLogger(MenuAppState.class.getName());

	@ServiceRef
	protected static Environments environments;

	private EnvironmentManager manager;
	private TerrainLoader loader;
	private boolean cloning;
	private Thread cloneThread;
	private boolean loading;

	private ActionMenuBar menuBar;

	private File cloningTerrainDirFile;

	private AppAction close;

	private AppAction map;

	private AppAction export;

	private AppAction timeOfDay;

	private AppAction editEnvironment;

	private AppAction editEnvironmentConfiguration;

	public MenuAppState(TerrainLoader loader, Preferences prefs, EnvironmentLight environmentLight, Node gameNode,
			Node weatherNode) {
		super(prefs);
		this.loader = loader;
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

	@Override
	protected void postInitialize() {
		manager = EnvironmentManager.get(assetManager);

		ActionAppState appState = app.getStateManager().getState(ActionAppState.class);
		menuBar = appState.getMenuBar();
		menuBar.invalidate();

		/* Menus */
		menuBar.addActionMenu(new ActionMenu("File", 0));
		menuBar.addActionMenu(new ActionMenu("Terrain", 10));
		menuBar.addActionMenu(new ActionMenu("Environment", 20));
		menuBar.addActionMenu(new ActionMenu("Help", 30));

		/* Actions */
		menuBar.addAction(new AppAction("Open Terrain Folder", evt -> openTerrainFolder()).setMenu("File"));
		menuBar.addAction(close = new AppAction("Close Terrain", evt -> closeTerrain()).setMenu("File"));
		menuBar.addAction(export = new AppAction("Export Terrain",
				evt -> exportTerrainTemplate(loader.getDefaultTerrainTemplate())).setMenu("File"));
		menuBar.addAction(map = new AppAction("Map", evt -> toggleMap()).setMenu("File"));
		menuBar.addAction(new AppAction("Options", evt -> toggleOptions()).setMenu("File").setMenuGroup(80));
		menuBar.addAction(new AppAction("Exit", evt -> exitApp()).setMenu("File").setMenuGroup(99));

		/* Environment menu */
		menuBar.addAction(new AppAction("New", evt -> newEnvironment()).setMenu("Environment"));
		menuBar.addAction(new AppAction(new ActionMenu("Open")).setMenu("Environment"));
		menuBar.addAction(editEnvironment = new AppAction("Edit", evt -> editEnvironment()).setMenu("Environment"));
		menuBar.addAction(new AppAction("Close", evt -> stateManager.getState(EnvironmentSwitcherAppState.class)
				.setEnvironment(EnvPriority.VIEWING, null)).setMenu("Environment"));
		menuBar.addAction(new AppAction(new ActionMenu("Configurations")).setMenu("Environment"));
		menuBar.addAction(timeOfDay = new AppAction(new ActionMenu("Time Of Day")).setMenu("Environment"));

		/* Time Of Day */
		menuBar.addAction(
				new AppAction(Icelib.toEnglish(EnvironmentPhase.SUNRISE), evt -> setPhase(EnvironmentPhase.SUNRISE))
						.setMenu("Time Of Day"));
		menuBar.addAction(new AppAction(Icelib.toEnglish(EnvironmentPhase.DAY), evt -> setPhase(EnvironmentPhase.DAY))
				.setMenu("Time Of Day"));
		menuBar.addAction(
				new AppAction(Icelib.toEnglish(EnvironmentPhase.SUNSET), evt -> setPhase(EnvironmentPhase.SUNSET))
						.setMenu("Time Of Day"));
		menuBar.addAction(
				new AppAction(Icelib.toEnglish(EnvironmentPhase.NIGHT), evt -> setPhase(EnvironmentPhase.NIGHT))
						.setMenu("Time Of Day"));

		/* Environment configurations */
		menuBar.addAction(new AppAction(new ActionMenu("New")).setMenu("Configurations"));
		menuBar.addAction(
				new AppAction("Enhanced", evt -> newEnvironmentConfiguration(EnhancedEnvironmentConfiguration.class))
						.setMenu("New"));
		menuBar.addAction(
				new AppAction("Legacy", evt -> newEnvironmentConfiguration(LegacyEnvironmentConfiguration.class))
						.setMenu("New"));
		menuBar.addAction(editEnvironmentConfiguration = new AppAction("Edit", evt -> editEnvironmentConfiguration())
				.setMenu("Configurations"));
		menuBar.addAction(new AppAction(new ActionMenu("Open Configuration")).setMenu("Configurations"));

		/* Help Actions */
		menuBar.addAction(new AppAction("Contents", evt -> help()).setMenu("Help"));
		menuBar.addAction(new AppAction("About", evt -> helpAbout()).setMenu("Help"));

		menuBar.validate();

		/* Initial availability */
		loading = true;
		setAvailable();

		/* Listen for sky changes */
		EnvironmentSwitcherAppState state = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
		if (state != null)
			state.addListener(this);

		/* Background load the terrain menu */

		app.getWorldLoaderExecutorService().execute(new Runnable() {

			@Override
			public String toString() {
				return "Loading available terrain and environments";
			}

			@Override
			public void run() {

				final List<AppAction> actions = new ArrayList<>();

				for (String n : ((ServerAssetManager) app.getAssetManager())
						.getAssetNamesMatching(".*/Terrain-[a-zA-Z\\d[_]]*\\.cfg")) {
					TerrainTemplateConfiguration cfg = TerrainTemplateConfiguration.get(assetManager, n);
					if (!cfg.getBaseTemplateName().equals("Common") && !cfg.getBaseTemplateName().equals("Default")
							&& !cfg.equals(loader.getTerrainTemplate())) {
						boolean external = ((IcesceneApp) app).getAssets().isExternal(cfg.getAssetPath());
						actions.add(new AppAction(Icelib.toEnglish(cfg.getBaseTemplateName()),
								evt -> validateTerrainTemplate(cfg)).setMenu("Terrain")
										.setMenuGroup(external ? 0 : 10));
					}
				}

				for (String k : manager.getEnvironments()) {
					actions.add(new AppAction(k, evt -> stateManager.getState(EnvironmentSwitcherAppState.class)
							.setEnvironment(EnvPriority.VIEWING, k)).setMenu("Open"));
				}

				List<String> envs = manager.getEnvironmentConfigurations();
				for (String k : envs) {
					actions.add(new AppAction(k, evt -> {
						final EnvironmentSwitcherAppState env = stateManager
								.getState(EnvironmentSwitcherAppState.class);
						if (env instanceof EditableEnvironmentSwitcherAppState
								&& ((EditableEnvironmentSwitcherAppState) env).isEdit())
							env.setEnvironment(EnvPriority.EDITING, k);
						else
							env.setEnvironment(EnvPriority.VIEWING, k);
					}).setMenu("Open Configuration"));
				}

				app.enqueue(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						menuBar.invalidate();
						actions.forEach((a) -> menuBar.addAction(a));
						menuBar.validate();
						loading = false;
						setAvailable();
						return null;
					}
				});
			}
		});

		loader.addListener(this);
	}

	protected void setPhase(EnvironmentPhase phase) {
		EnvironmentSwitcherAppState sas = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
		sas.setPhase(phase);
	}

	@Override
	protected void onCleanup() {
		loader.removeListener(this);
		EnvironmentSwitcherAppState state = app.getStateManager().getState(EnvironmentSwitcherAppState.class);
		if (state != null)
			state.addListener(this);
	}

	private void newEnvironmentConfiguration(final Class<? extends AbstractEnvironmentConfiguration> clazz) {
		final InputBox dialog = new InputBox(screen, new Vector2f(15, 15), true) {
			{
				setStyleClass("large");
			}

			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				manager.newConfiguration(text, clazz);
				hide();
				EditableEnvironmentSwitcherAppState sas = app.getStateManager()
						.getState(EditableEnvironmentSwitcherAppState.class);
				sas.setEnvironment(EnvPriority.VIEWING, text);
				sas.setEdit(true);
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.setWindowTitle("New Environment");
		dialog.setButtonOkText("Create");
		dialog.setMsg("");
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	private void newEnvironment() {
		changeEnvironment("New Environment");

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

	private void toggleMap() {
		TerrainMapAppState as = app.getStateManager().getState(TerrainMapAppState.class);
		if (as == null) {
			app.getStateManager().attach(new TerrainMapAppState(app.getPreferences()));
		} else {
			app.getStateManager().detach(as);
		}
	}

	private void exportTerrainTemplate(final TerrainTemplateConfiguration template) {
		final InputBox dialog = new InputBox(screen, new Vector2f(15, 15), true) {
			{
				setStyleClass("large");
			}

			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
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
								public void run(TerrainTemplateConfiguration targetTemplate, Frame input) {
									setAvailable();
									input.hide();
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
				hide();
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.setWindowTitle("Export Terrain");
		dialog.setButtonOkText("Export");
		dialog.setMsg(template.getBaseTemplateName());
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	private void closeTerrain() {
		if (loader.isNeedsSave()) {
			final DialogBox dialog = new DialogBox(screen, new Vector2f(15, 15), true) {
				{
					setStyleClass("large");
				}

				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
					closeEditor();
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Confirm Close Template");
			dialog.setButtonOkText("Close");
			dialog.setText("You have unsaved edits! Are you sure you wish to close this template?");
			dialog.setResizable(false);
			dialog.setMovable(false);
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		} else {
			closeEditor();
		}
	}

	private void helpAbout() {
		AlertBox alert = new AlertBox(screen, true) {

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}
		};
		alert.setModal(true);
		alert.setTitle("About");
		alert.setText("<h1>" + AppInfo.getName() + "</h1><h4>Version " + AppInfo.getVersion() + "</h4>");
		screen.showElement(alert, ScreenLayoutConstraints.center);
	}

	private void help() {
		HelpAppState has = app.getStateManager().getState(HelpAppState.class);
		if (has == null) {
			app.getStateManager().attach(new HelpAppState(prefs));
		} else {
			app.getStateManager().detach(has);
		}
	}

	private void exitApp() {
		if (loader.isNeedsSave()) {
			final DialogBox dialog = new DialogBox(screen, new Vector2f(15, 15), true) {
				{
					setStyleClass("large");
				}

				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					app.stop();
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Confirm Exit");
			dialog.setButtonOkText("Exit");
			dialog.setText("You have unsaved edits! Are you sure you wish to exit?");
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		} else {
			if (cloning) {
				LOG.info("Interrupting cloneing");
				cloneThread.interrupt();
				LOG.info("Interrupted cloneing");
			}
			app.stop();
		}
	}

	private void setAvailable() {
		menuBar.setEnabled(!loading && !cloning);
		close.setEnabled(loader.getDefaultTerrainTemplate() != null);
		map.setEnabled(loader.getDefaultTerrainTemplate() != null);
		export.setEnabled(loader.getDefaultTerrainTemplate() != null);
		EditableEnvironmentSwitcherAppState env = app.getStateManager()
				.getState(EditableEnvironmentSwitcherAppState.class);
		timeOfDay.setEnabled(env != null && env.getEnvironment() == null);
		editEnvironmentConfiguration.setEnabled(env != null && env.getEnvironmentConfiguration() != null);
		editEnvironment.setEnabled(env != null && env.getEnvironment() != null);
	}

	private void toggleOptions() {
		final OptionsAppState state = stateManager.getState(OptionsAppState.class);
		if (state == null) {
			stateManager.attach(new OptionsAppState(prefs));
		} else {
			stateManager.detach(state);
		}
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

			final DialogBox dialog = new DialogBox(screen, true) {
				{
					setStyleClass("large");
				}

				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					// Clone#
					hide();
					askForCloneName(template);
				}

				@Override
				public void createButtons(BaseElement buttons) {
					PushButton btnReadOnly = new PushButton(screen, "Read-Only") {
						{
							setStyleClass("fancy");
						}
					};
					btnReadOnly.onMouseReleased(evt -> {
						loadTerrainTemplate(template, false);
						hide();
					});
					buttons.addElement(btnReadOnly);
					form.addFormElement(btnReadOnly);
					super.createButtons(buttons);
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Read-Only Template");
			dialog.setButtonOkText("Clone");
			dialog.setText(String.format("The template %s is provided by the server, so cannot be "
					+ "directly edited. You may either CLONE the terrain for local editing, or "
					+ "open the template in read-only mode.", template.getBaseTemplateName()));
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		} else {
			LOG.info(String.format("Local terrain folder %s exists, opening", localTerrainFolder.getAbsolutePath()));
			loadTerrainTemplate(template, true);
		}
	}

	private void closeEditor() {
		loader.setTerrainTemplate(null);
		if (TerrainEditorAppState.isEditing(stateManager)) {
			TerrainEditorAppState.toggle(stateManager);
		}
	}

	private void askForCloneName(final TerrainTemplateConfiguration sourceTemplate) {
		final InputBox dialog = new InputBox(screen, true) {
			{
				setStyleClass("large new-clone");
			}

			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				// Check the name doesn't already exist
				File f = MenuAppState.this.app.getAssets()
						.getExternalAssetFile(String.format("%s/%s", SceneConstants.TERRAIN_PATH, text));
				if (f.exists()) {
					error(String.format("The template %s already exists locally."));
					hide();
				} else {
					// Close existing terrain
					closeEditor();

					// Clone#
					hide();

					String terrainDir = sourceTemplate.getAssetFolder();
					String terrainName = text.replace(" ", "").replace("/", "").replace("\\", "");
					terrainDir = Icelib.getDirname(terrainDir) + "/Terrain-" + terrainName;
					LOG.info(String.format("New terrain directory will be %s", terrainDir));
					cloneTerrainTemplate(CloneType.iceclient, sourceTemplate, terrainName, terrainDir,
							MenuAppState.this.app.getAssets().getExternalAssetsFolder(), new OnCloneCallback() {

								@Override
								public void run(TerrainTemplateConfiguration targetTemplate, Frame input) {
									setAvailable();
									input.hide();
									loadTerrainTemplate(targetTemplate, true);

								}
							});
				}
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.setWindowTitle("New Clone");
		dialog.setButtonOkText("Clone");
		dialog.setMsg(sourceTemplate.getBaseTemplateName() + " Copy");
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
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

		final Indicator overallProgress = new Indicator(screen, Orientation.HORIZONTAL);
		overallProgress.setMaxValue(useFileCount ? assetsMatching.size() : 100);
		overallProgress.setCurrentValue(0);

		final Frame w = new Frame(screen, false) {
			{
				setStyleClass("clone-progress large");
			}
		};
		w.getContentArea().setLayoutManager(new MigLayout(screen, "fill, wrap 1", "[grow]", "[][]"));
		w.setWindowTitle("Cloning");
		w.getContentArea().addElement(progressTitle, "growx");
		w.getContentArea().addElement(overallProgress, "growx");
		screen.showElement(w, ScreenLayoutConstraints.center);

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
							w.hide();
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

		loader.setReadOnly(!writeable);
		loader.setDefaultTerrainTemplate(template, position, defaultRotation);

	}

	private File getTerrainFolder() {
		return new File(((IcesceneApp) app).getAssets().getExternalAssetsFolder(), SceneConstants.TERRAIN_PATH);
	}

	protected void openTerrainFolder() {
		final File terrainFolder = getTerrainFolder();
		try {
			XDesktop.getDesktop().open(terrainFolder);
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, String.format("Failed to open terrain folder %s", terrainFolder), ex);
			error(String.format("Failed to open terrain folder %s", terrainFolder), ex);
		}
	}

	@Override
	public void phaseChanged(EnvironmentPhase phase) {
		setAvailable();
	}

	@Override
	public void environmentChanged(String environment) {
		setAvailable();
	}

	@Override
	public void environmentConfigurationChanged(AbstractEnvironmentConfiguration topEnv) {
		setAvailable();
	}
}
