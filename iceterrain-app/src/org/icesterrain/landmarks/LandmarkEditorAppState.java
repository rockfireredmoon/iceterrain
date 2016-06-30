package org.icesterrain.landmarks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icescene.Alarm;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConfig;
import org.icesquirrel.interpreter.SquirrelInterpretedTable;
import org.icesquirrel.runtime.SquirrelPrinter;
import org.icesquirrel.runtime.SquirrelTable;
import org.icesterrain.landmarks.LandmarkAppState.Listener;
import org.iceterrain.TerrainAppState;
import org.iceui.HPosition;
import org.iceui.VPosition;
import org.iceui.controls.ElementStyle;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyInputBox;
import org.iceui.controls.FancyPersistentWindow;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.SaveType;
import org.iceui.controls.SelectArea;
import org.iceui.controls.UIUtil;
import org.iceui.controls.ZMenu;

import com.jme3.app.state.AppStateManager;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;

import icetone.controls.buttons.ButtonAdapter;
import icetone.controls.text.Label;
import icetone.controls.text.TextField;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.layout.mig.MigLayout;

/**
 * Displays landmarks and allows add, delete, warp to, copy etc.
 */
public class LandmarkEditorAppState extends IcemoonAppState<IcemoonAppState<?>> implements Listener {
	final static Logger LOG = Logger.getLogger(LandmarkEditorAppState.class.getName());

	private enum LandmarkMenuOption {

		REMOVE, WARP_TO, COPY
	}

	private TextField filter;
	private FancyPersistentWindow landmarksWindow;
	private SelectArea landmarkList;
	private ButtonAdapter removeLandmark;
	private Alarm.AlarmTask filterAlarmTask;
	private LandmarkAppState landmarkManager;
	private FancyButton paste;

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
		landmarksWindow = new FancyPersistentWindow(screen, SceneConfig.LANDMARKS,
				screen.getStyle("Common").getInt("defaultWindowOffset"), VPosition.TOP, HPosition.LEFT, new Vector2f(280, 400),
				FancyWindow.Size.SMALL, true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				stateManager.detach(LandmarkEditorAppState.this);
			}
		};
		landmarksWindow.setWindowTitle("Landmarks");
		landmarksWindow.setIsMovable(true);
		landmarksWindow.setIsResizable(true);
		landmarksWindow.setDestroyOnHide(true);

		// Status area
		Element filterArea = new Element(screen);
		filterArea.setLayoutManager(new MigLayout(screen, "ins 0, wrap 2", "[][fill, grow]", "[]"));
		Label l = new Label(screen);
		l.setText("Filter");
		ElementStyle.small(screen, l);
		filterArea.addChild(l);

		//
		filter = new TextField(screen) {
			@Override
			public void controlKeyPressHook(KeyInputEvent evt, String text) {
				cancelFilter();
				filterAlarmTask = LandmarkEditorAppState.this.app.getAlarm().timed(new Callable<Void>() {
					public Void call() throws Exception {
						reloadLandmarks();
						filterAlarmTask = null;
						return null;
					}
				}, 0.75f);
			}
		};
		filter.setToolTipText("Filter your landmarks");
		filterArea.addChild(filter);

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
		landmarkList.setIsMovable(false);
		landmarkList.setIsResizable(false);

		// Buttons
		Container buttons = new Container(screen);
		buttons.setLayoutManager(new MigLayout(screen, "", "push[][][]push"));
		ButtonAdapter add = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				addLandmark();
			}
		};
		add.setText("Add");
		add.setToolTipText("Add landmark");
		buttons.addChild(add);
		removeLandmark = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				removeLandmark(getSelectedLandmark());
			}
		};
		removeLandmark.setText("Remove");
		removeLandmark.setToolTipText("Remove landmark");
		buttons.addChild(removeLandmark);
		paste = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				String text = screen.getClipboardText();
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
			}
		};
		paste.setText("Paste");
		paste.setToolTipText("Paste from clipboard");
		buttons.addChild(paste);
		// This
		final Element contentArea = landmarksWindow.getContentArea();
		contentArea.setLayoutManager(new MigLayout(screen, "wrap 1", "[fill, grow]", "[][fill, grow][]"));
		contentArea.addChild(filterArea);
		contentArea.addChild(landmarkList);
		contentArea.addChild(buttons);

		// Show with an effect and sound
		screen.addElement(landmarksWindow);
		landmarksWindow.hide();
		landmarksWindow.showWithEffect();

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
		if (landmarksWindow.getIsVisible()) {
			landmarksWindow.hideWithEffect();
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
		ZMenu subMenu = new ZMenu(screen) {

			@Override
			protected void itemSelected(ZMenu originator, ZMenuItem item) {
				super.itemSelected(originator, item);
				Object value = item.getValue();
				switch ((LandmarkMenuOption) value) {
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
					screen.setClipboardText(SquirrelPrinter.format(t, 0));
					info("Copied landmark to clipboard");
					break;
				}
			}
		};
		subMenu.addMenuItem("Remove", LandmarkMenuOption.REMOVE);
		subMenu.addMenuItem("Warp To Landmark", LandmarkMenuOption.WARP_TO);
		subMenu.addMenuItem("Copy to clipboard", LandmarkMenuOption.COPY);
		screen.addElement(subMenu);
		subMenu.showMenu(null, x, y);
	}

	private void addLandmark() {
		FancyInputBox fib = new FancyInputBox(screen, Vector2f.ZERO, FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, final String text, boolean toggled) {
				hideWindow();
				try {
					TerrainAppState tas = app.getStateManager().getState(TerrainAppState.class);
					Landmark lm = new Landmark(tas.getPlayerViewLocation(), tas.getApp().getCamera().getRotation(),
							tas.getTerrainTemplate().getBaseTemplateName(), text);
					landmarkManager.addLandmark(lm);
					info(String.format("Added landmark '%s' at %6.1f, %6.1f, %6.1f on %s", lm.getName(), lm.getLocation().x,
							lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
				} catch (Exception iae) {
					LOG.log(Level.SEVERE, "Failed to add landmark.", iae);
					error("Failed to add landmark.", iae);
				}
			}
		};
		fib.setDestroyOnHide(true);
		fib.setWindowTitle("Add Landmark");
		fib.setButtonOkText("Add Landmark");
		fib.sizeToContent();
		fib.setWidth(300);
		fib.setIsResizable(false);
		fib.setIsMovable(false);
		fib.sizeToContent();
		UIUtil.center(screen, fib);
		screen.addElement(fib, null, true);
		fib.showAsModal(true);
	}

	private void removeLandmark(final Landmark landmark) {
		final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
				try {
					Landmark lm = getSelectedLandmark();
					landmarkManager.removeLandmark(lm);
					info(String.format("Removed landmark '%s' at %6.1f, %6.1f, %6.1f on %s", lm.getName(), lm.getLocation().x,
							lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
				} catch (Exception iae) {
					LOG.log(Level.SEVERE, "Failed to remove landmark.", iae);
					error("Failed to remove landmark.", iae);
				}
				hideWindow();
			}
		};
		dialog.getDragBar().setFontColor(ColorRGBA.Orange);
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		dialog.setWindowTitle("Confirm Removal");
		dialog.setMsg(String.format("Are you sure you wish to remove your landmark '%s'?", landmark.getName()));
		dialog.sizeToContent();
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	private void setAvailable() {
		removeLandmark.setIsEnabled(landmarkList.isAnySelected());
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
