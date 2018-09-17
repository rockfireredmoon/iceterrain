package org.iceterrain.landmarks;

import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.console.AbstractCommand;
import org.icescene.console.Command;
import org.iceterrain.TerrainAppState;

@Command(names = "landmark")
public class LandmarkCommand extends AbstractCommand {

	public boolean run(String cmdName, CommandLine commandLine) {
		String[] args = commandLine.getArgs();
		LandmarkAppState landmarks = app.getStateManager().getState(LandmarkAppState.class);
		TerrainAppState tas = app.getStateManager().getState(TerrainAppState.class);
		if (args.length == 0) {
			console.outputError("Must supply a single option. list, add or remove");
			return false;
		} else {
			if (args[0].equalsIgnoreCase("list")) {
				List<Landmark> l = landmarks.getLandmarks();
				if (l.isEmpty()) {
					console.outputError(String.format("No landmarks."));
					return false;
				} else {
					for (Landmark lm : l) {
						console.output(String.format("%-20s %6.1f, %6.1f, %6.1f %-20s", lm.getName(), lm.getLocation().x,
								lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
					}
				}
			} else if (args[0].equalsIgnoreCase("add")) {
				if (args.length == 2) {
					TerrainTemplateConfiguration terrainTemplate = tas.getTerrainTemplate();
					if (terrainTemplate == null) {
						console.outputError(String.format("No terrain is currently loaded."));
						return false;
					} else {
						Landmark lm = new Landmark(tas.getPlayerViewLocation(), app.getCamera().getRotation(), terrainTemplate.getBaseTemplateName(), args[1]);
						landmarks.addLandmark(lm);
						console.output(String.format("Added landmark '%s' at %6.1f, %6.1f, %6.1f on %s", lm.getName(),
								lm.getLocation().x, lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
					}
				} else {
					console.outputError(String
							.format("'add' command requires single argument, the name. You may use double quotes if the name contains spaces."));
					return false;
				}
			} else if (args[0].equalsIgnoreCase("remove")) {
				if (args.length == 2) {
					try {
						Landmark lm = landmarks.getLandmark(args[1]);
						landmarks.removeLandmark(lm);
						console.output(String.format("Removed landmark '%s' at %6.1f, %6.1f, %6.1f on %20s", lm.getName(),
								lm.getLocation().x, lm.getLocation().y, lm.getLocation().z, lm.getTerrain()));
					} catch (IllegalArgumentException iae) {
						console.error(String.format("Unknown landmark '%s'.", args[1]));
						return false;
					}
				} else {
					console.outputError(String
							.format("'remove' command requires single argument, the name. You may use double quotes if the name contains spaces."));
					return false;
				}
			} else {
				console.outputError(String.format("Invalid command '%s', must supply a single option. list, add or remove.",
						args[0]));
				return false;
			}
		}
		// Point3D loc =
		// console.getApp().getStateManager().getState(GameAppState.class).getSpawn().getLocation();
		// console.getApp().getScreen().setClipboardText(String.format("%6.3f,%6.3f,%6.3f",
		// loc.x, loc.y, loc.z));
		return true;
	}
}
