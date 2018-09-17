package org.iceterrain.landmarks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icesquirrel.interpreter.SquirrelInterpretedTable;
import org.icesquirrel.runtime.SquirrelPrinter;
import org.icesquirrel.runtime.SquirrelTable;
import org.iceterrain.TerrainAppState;
import org.iceterrain.landmarks.LandmarkAppState.Listener;
import org.iceui.controls.ElementStyle;
import org.iceui.controls.SelectArea;

import com.jme3.app.state.AppStateManager;
import com.jme3.font.BitmapFont.Align;
import com.jme3.font.BitmapFont.VAlign;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;

import icetone.controls.buttons.PushButton;
import icetone.controls.menuing.Menu;
import icetone.controls.text.Label;
import icetone.controls.text.TextField;
import icetone.core.BaseElement;
import icetone.core.Size;
import icetone.core.StyledContainer;
import icetone.core.ToolKit;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.mig.MigLayout;
import icetone.core.utils.Alarm;
import icetone.extras.windows.DialogBox;
import icetone.extras.windows.InputBox;
import icetone.extras.windows.PersistentWindow;
import icetone.extras.windows.SaveType;

/**
 * Displays landmarks and allows add, delete, warp to, copy etc.
 */
public class LandmarkEditorAppState extends IcemoonAppState<IcemoonAppState<?>> implements Listener {
	final static Logger LOG = Logger.getLogger(LandmarkEditorAppState.class.getName());

	private enum LandmarkMenuOption {

		REMOVE, WARP_TO, COPY
	}

	private TextField filter;
	private PersistentWindow landmarksWindow;
	private SelectArea landmarkList;
	private PushButton removeLandmark;
	private Alarm.AlarmTask filterAlarmTask;
	private LandmarkAppState landmarkManager;
	private PushButton paste;

	public LandmarkEditorAppState(Preferences node) {
		super(node);
	}

	@Override
	protected IcemoonAppState<?> onInitialize(final AppStateManager stateManager, final IcesceneApp app) {
		return null;
	}

	@Override
	protected void postInitialize() {
		landmarkManager = stateManager.getState(LandmarkAppState.class);

		// / Minmap window
		landmarksWindow = new PersistentWindow(screen, SceneConfig.LANDMARKS, VAlign.Top, Align.Left,
				new Size(280, 400), true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				stateManager.detach(LandmarkEditorAppState.this);
			}
		};
		landmarksWindow.setWindowTitle("Landmarks");
		landmarksWindow.setMovable(true);
		landmarksWindow.setResizable(true);
		landmarksWindow.setDestroyOnHide(true);

		// Status area
		BaseElement filterArea = new BaseElement(screen);
		filterArea.setLayoutManager(new MigLayout(screen, "ins 0, wrap 2", "[][fill, grow]", "[]"));
		Label l = new Label(screen);
		l.setText("Filter");
		ElementStyle.normal(l);
		filterArea.addElement(l);

		//
		filter = new TextField(screen);
		filter.onKeyboardReleased(evt -> {
			cancelFilter();
			filterAlarmTask = LandmarkEditorAppState.this.app.getAlarm().timed(new Callable<Void>() {
				public Void call() throws Exception {
					reloadLandmarks();
					filterAlarmTask = null;
					return null;
				}
			}, 0.75f);
		});
		filter.setToolTipText("Filter your landmarks");
		filterArea.addElement(filter);

		// Landmark list
		landmarkList = new SelectArea(screen) {
			@Override
			public void onChange() {
				setAvailable();
			}

			@Override
			protected void onRightClickSelection(MouseButtonEvent evt) {
				setAvailable();
				showLandmarkMenu(getSelectedLandmark(), evt.getX() + 20, evt.getY() + 20);
			}
		};
		landmarkList.setMovable(false);
		landmarkList.setResizable(false);

		// Buttons
		StyledContainer buttons = new StyledContainer(screen);
		buttons.setLayoutManager(new MigLayout(screen, "", "push[][][]push"));
		PushButton add = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		add.onMouseReleased(evt -> addLandmark());
		add.setText("Add");
		add.setToolTipText("Add landmark");
		buttons.addElement(add);
		removeLandmark = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		removeLandmark.onMouseReleased(evt -> removeLandmark(getSelectedLandmark()));
		removeLandmark.setText("Remove");
		removeLandmark.setToolTipText("Remove landmark");
		buttons.addElement(removeLandmark);
		paste = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		paste.onMouseReleased(evt -> {
			String text = ToolKit.get().getClipboardText();
			try {
				SquirrelTable arr = SquirrelInterpretedTable.table(text);
				if (arr.size() != 1) {
					error("Clipboard must contain a single landmark.");
				} else {
					Map.Entry<Object, Object> en = arr.entrySet().iterator().next();
					String name = (String) en.getKey();
					try {
						landmarkManager.getLandmark(name);
						error(String.format("A landmark named %s already exists.", name));
					} catch (IllegalArgumentException iae) {
						// Doesn't exist, ok to paste
						SquirrelTable t = (SquirrelTable) en.getValue();
						Landmark m = new Landmark(name, t);
						landmarkManager.addLandmark(m);
						info(String.format("Added landmark '%s' for %s", m.getName(), m.getTerrain()));
					}
				}
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to paste landmark from clipboard", e);
				error("Failed to past landmark from clipboard.", e);
			}
		});
		paste.setText("Paste");
		paste.setToolTipText("Paste from clipboard");
		buttons.addElement(paste);
		// This
		final BaseElement contentArea = landmarksWindow.getContentArea();
		contentArea.setLayoutManager(new MigLayout(screen, "wrap 1", "[fill, grow]", "[][fill, grow][]"));
		contentArea.addElement(filterArea);
		contentArea.addElement(landmarkList);
		contentArea.addElement(buttons);

		// Show with an effect and sound
		screen.showElement(landmarksWindow);

		// Load initial landmarks
		reloadLandmarks();

		landmarkManager.addListener(this);

	}

	@Override
	public void update(float tpf) {
	}

	public void message(String text) {
	}

	@Override
	public void landmarkChanged(Landmark landmark) {
		reloadOnQueue();
	}

	@Override
	public void landmarkAdded(Landmark landmark) {
		reloadOnQueue();
	}

	@Override
	public void landmarkRemoved(Landmark landmark) {
		reloadOnQueue();
	}

	@Override
	protected void onCleanup() {
		cancelFilter();
		if (landmarksWindow.isVisible()) {
			landmarksWindow.hide();
		}
		landmarkManager.removeListener(this);
	}

	private void reloadOnQueue() {
		app.enqueue(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				reloadLandmarks();
				return null;
			}
		});
	}

	private void cancelFilter() {
		if (filterAlarmTask != null) {
			filterAlarmTask.cancel();
			filterAlarmTask = null;
		}
	}

	private Landmark getSelectedLandmark() {
		final LandmarkPanel p = (LandmarkPanel) landmarkList.getSelectedItem();
		return p == null ? null : p.getLandmark();
	}

	private void showLandmarkMenu(final Landmark landmark, float x, float y) {
		Menu<LandmarkMenuOption> subMenu = new Menu<LandmarkMenuOption>(screen);
		subMenu.onChanged((evt) -> {
			switch (evt.getNewValue().getValue()) {
			case REMOVE:
				removeLandmark(landmark);
				break;
			case WARP_TO:
				landmarkManager.warpTo(landmark);
				break;
			case COPY:
				SquirrelTable table = landmark.toTable();
				SquirrelTable t = new SquirrelTable();
				t.put(landmark.getName(), table);
				ToolKit.get().setClipboardText(SquirrelPrinter.format(t, 0));
				info("Copied landmark to clipboard");
				break;
			}
		});
		subMenu.addMenuItem("Remove", LandmarkMenuOption.REMOVE);
		subMenu.addMenuItem("Warp To Landmark", LandmarkMenuOption.WARP_TO);
		subMenu.addMenuItem("Copy to clipboard", LandmarkMenuOption.COPY);
		screen.addElement(subMenu);
		subMenu.showMenu(null, x, y);
	}

	private void addLandmark() {
		InputBox fib = new InputBox(screen, Vector2f.ZERO, true) {
			{
				setStyleClass("large");
			}

			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, final String text, boolean toggled) {
				hide();
				try {
					TerrainAppState tas = app.getStateManager().getState(TerrainAppState.class);
					Landmark lm = new Landmark(tas.getPlayerViewLocation(), tas.getApp().getCamera().getRotation(),
							tas.getTerrainTemplate().getBaseTemplateName(), text);
					landmarkManager.addLandmark(lm);
					info(String.format("Added landmark '%s' at %6.1f, %6.1f, %6.1f on %s", lm.getName(),
							lm.getLocation().x, lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
				} catch (Exception iae) {
					LOG.log(Level.SEVERE, "Failed to add landmark.", iae);
					error("Failed to add landmark.", iae);
				}
			}
		};
		fib.setDestroyOnHide(true);
		fib.setWindowTitle("Add Landmark");
		fib.setButtonOkText("Add Landmark");
		fib.setResizable(false);
		fib.setMovable(false);
		fib.sizeToContent();
		fib.setModal(true);
		screen.showElement(fib, ScreenLayoutConstraints.center);
	}

	private void removeLandmark(final Landmark landmark) {
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
				try {
					Landmark lm = getSelectedLandmark();
					landmarkManager.removeLandmark(lm);
					info(String.format("Removed landmark '%s' at %6.1f, %6.1f, %6.1f on %s", lm.getName(),
							lm.getLocation().x, lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
				} catch (Exception iae) {
					LOG.log(Level.SEVERE, "Failed to remove landmark.", iae);
					error("Failed to remove landmark.", iae);
				}
				hide();
			}
		};
		dialog.getDragBar().setFontColor(ColorRGBA.Orange);
		dialog.setWindowTitle("Confirm Removal");
		dialog.setText(String.format("Are you sure you wish to remove your landmark '%s'?", landmark.getName()));
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	private void setAvailable() {
		removeLandmark.setEnabled(landmarkList.isAnySelected());
	}

	private void reloadLandmarks() {
		final List<Landmark> landmarks = landmarkManager.getLandmarks();
		app.enqueue(new Callable<Void>() {
			public Void call() throws Exception {
				landmarkList.removeAllListItems();
				for (Landmark landmark : landmarks) {
					String filterText = filter.getText().trim().toLowerCase();
					if (filterText.equals("") || landmark.getName().toLowerCase().contains(filterText)
							|| landmark.getTerrain().toLowerCase().contains(filterText)) {
						final LandmarkPanel fp = new LandmarkPanel(screen, landmark);
						landmarkList.addScrollableContent(fp);
					}
				}
				setAvailable();
				return null;
			}
		});
	}
}
