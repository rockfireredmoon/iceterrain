package org.iceterrain.app;

import org.apache.commons.cli.CommandLine;
import org.icescene.configuration.TerrainTemplateConfiguration;
import org.icescene.console.AbstractCommand;
import org.icescene.console.Command;
import org.iceterrain.TerrainAppState;
import org.iceterrain.TerrainLoader;

import com.jme3.math.Vector3f;

@Command(names = "warpt")
public class WarpToTerrainTileCommand extends AbstractCommand {

	public WarpToTerrainTileCommand() {
		argHelp = "<x> <y>";
		description = "Warps the camera to a tile location (the actual warp location being " + "dependent on the tile page size.";
	}

	public boolean run(String cmdName, CommandLine commandLine) {
		TerrainAppState tas = app.getStateManager().getState(TerrainAppState.class);
		if (tas == null) {
			console.outputError("No terrain appstate loaded.");
		} else {
			TerrainLoader tl = tas.getTerrainLoader();
			if (tl == null || tas.getTerrainTemplate() == null) {
				console.outputError("No terrain loaded.");
			} else {
				TerrainTemplateConfiguration cfg = tl.getTerrainTemplate();
				int z = Integer.parseInt((String) commandLine.getArgList().get(1));
				int x = Integer.parseInt((String) commandLine.getArgList().get(0));
				Vector3f newLocation = new Vector3f(cfg.getPageWorldX() * x, app.getCamera().getLocation().y, cfg.getPageWorldZ() * z);
				app.getCamera().setLocation(newLocation);
				console.output(String.format("Warped to tile %d,%d (%s)", z, x, newLocation));
				return true;
			}
		}
		return false;
	}
}
