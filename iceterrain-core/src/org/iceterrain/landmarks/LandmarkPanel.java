package org.iceterrain.landmarks;

import org.iceui.controls.ElementStyle;

import com.jme3.font.BitmapFont;

import icetone.controls.buttons.SelectableItem;
import icetone.controls.text.Label;
import icetone.core.BaseScreen;
import icetone.core.layout.mig.MigLayout;

/**
 * Component for showing details of a landmark in the landmark list
 */
public class LandmarkPanel extends SelectableItem  {

    private final Landmark landmark;

    public LandmarkPanel(BaseScreen screen, Landmark character) {
        super(screen);
        this.landmark = character;
        setLayoutManager(new MigLayout(screen, "ins 0, wrap 1", "[grow, fill]", "[align top][align bottom]"));
        setIgnoreMouse(true);
        
        // Name
        Label nameLabel = new Label(screen);
        nameLabel.setTextVAlign(BitmapFont.VAlign.Top);
        nameLabel.setText(character.getName());
        ElementStyle.normal(nameLabel, true, false);
        addElement(nameLabel);

        // Location
        Label details = new Label(screen);
        details.setText(String.format("Location: %6.1f %6.1f %6.1f", character.getLocation().x, character.getLocation().y, character.getLocation().z));        
        ElementStyle.normal(details);
        addElement(details);

        // Terrain
        Label terrain = new Label(screen);
        terrain.setText(String.format("%s", character.getTerrain()));
        ElementStyle.normal(terrain, true, false);
        addElement(terrain);
    }
    
    public Landmark getLandmark() {
        return landmark;
    }

}
