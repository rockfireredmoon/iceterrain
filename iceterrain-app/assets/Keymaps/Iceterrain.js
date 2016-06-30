__KeyMaps = __KeyMaps;
with (JavaImporter(org.icescene.io, com.jme3.input.controls, com.jme3.input)) {
	__KeyMaps.Console = {
		trigger : new KeyTrigger(KeyInput.KEY_GRAVE),
		category : "Other"
	};
	__KeyMaps.Options = {
		trigger : new KeyTrigger(KeyInput.KEY_O),
		category : "Windows"
	};
	__KeyMaps.Landmarks = {
		trigger : new KeyTrigger(KeyInput.KEY_L),
		category : "Windows"
	};
	__KeyMaps.Undo = {
		trigger : new KeyTrigger(KeyInput.KEY_Z),
		modifiers : ModifierKeysAppState.CTRL_MASK,
		category : "Editing"
	};
	__KeyMaps.Redo = {
		trigger : new KeyTrigger(KeyInput.KEY_Y),
		modifiers : ModifierKeysAppState.CTRL_MASK,
		category : "Editing"
	};
};