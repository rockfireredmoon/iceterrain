package org.icesterrain.landmarks;

import org.iceui.controls.ElementStyle;
import org.iceui.controls.SelectableItem;

import com.jme3.font.BitmapFont;

import icetone.controls.text.Label;
import icetone.core.ElementManager;
import icetone.core.layout.mig.MigLayout;

/**
 * Component for showing details of a landmark in the landmark list
 */
public class LandmarkPanel extends SelectableItem  {

    private final Landmark landmark;

    public LandmarkPanel(ElementManager screen, Landmark character) {
        super(screen);
        this.landmark = character;
        setLayoutManager(new MigLayout(screen, "ins 0, wrap 1", "[grow, fill]", "[align top][align bottom]"));
        setIgnoreMouse(true);
        
        // Name
        Label nameLabel = new Label(screen);
        nameLabel.setTextVAlign(BitmapFont.VAlign.Top);
        nameLabel.setText(character.getName());
        ElementStyle.normal(screen, nameLabel, true, false);
        addChild(nameLabel);

        // Location
        Label details = new Label(screen);
        details.setText(String.format("Location: %6.1f %6.1f %6.1f", character.getLocation().x, character.getLocation().y, character.getLocation().z));        
        ElementStyle.small(screen, details);
        addChild(details);

        // Terrain
        Label terrain = new Label(screen);
        terrain.setText(String.format("%s", character.getTerrain()));
        ElementStyle.normal(screen, terrain, true, false);
        addChild(terrain);
    }
    
    public Landmark getLandmark() {
        return landmark;
    }

}
