package org.iceterrain.maps;

import org.apache.commons.cli.CommandLine;
import org.icescene.console.AbstractCommand;
import org.icescene.console.Command;
import org.iceterrain.TerrainAppState;

@Command(names = "terrain")
public class TerrainMapCommand extends AbstractCommand {

	public boolean run(String cmdName, CommandLine commandLine) {
		TerrainAppState tas = app.getStateManager().getState(TerrainAppState.class);
		if (tas == null) {
			console.outputError("No terrain appstate loaded.");
		} else {
			if (tas.getTerrainLoader() == null || tas.getTerrainTemplate() == null) {
				console.outputError("No terrain loaded.");
			} else {
				TerrainMapAppState as = app.getStateManager().getState(TerrainMapAppState.class);
				if (as == null) {
					app.getStateManager().attach(new TerrainMapAppState(app.getPreferences()));
				} else {
					app.getStateManager().detach(as);
				}
				return true;
			}
		}
		return false;
	}
}
