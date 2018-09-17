package org.iceterrain;

public class TerrainConstants {

	/**
	 * The preference key app settings are stored under
	 */
	public static String APPSETTINGS_NAME = "iceterrain";

	/**
	 * How long the mouse must be held before operations start repeating.
	 */
	public static float MOUSE_REPEAT_DELAY = 0.2f;

	/**
	 * How long between each repeat
	 */
	public static float MOUSE_REPEAT_INTERVAL = 0.05f;

	/**
	 * How much to scale editor brush by
	 */
	static float EDITOR_BRUSH_SCALE = 7.5f;

	/**
	 * The distance the sun representation is kept from the camera when in build
	 * mode
	 */
	public static float SUN_REPRESENTATION_DISTANCE = 3010f;
	/**
	 * Physical size of sun
	 */
	public static float SUN_SIZE = 50f;
	/**
	 * How often the environment can update the position of the sun (well,
	 * directional
	 * light)
	 */
	public static float SUN_POSITION_UPDATE_INTERVAL = 0.25f;
	/**
	 * The amount to factor any height brush amount by
	 */
	public static final float HEIGHT_BRUSH_FACTOR = 0.05f;
	/**
	 * The amount to factor the smooth brush
	 */
	public static final float SMOOTH_BRUSH_FACTOR = 0.1f;
}
