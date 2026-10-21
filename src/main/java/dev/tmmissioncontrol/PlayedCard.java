package dev.tmmissioncontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlayedCard {
    public String name;
    public String color;
    public String colorLabel;
    public List<String> tags = new ArrayList<>();
    public String extra = "";
    public int generation;
    public boolean blue;
    public boolean project = true;
    public boolean hasRequirement;
    public int number;
    public String tokenType;
    public int tokens;
    public int printedVp;
    /** Generation this blue card's action was last used; the page dims it for the rest of that generation. */
    public int actionUsedGen;
    /** Printed effects the catalog keeps as data rather than text, for card popovers. */
    public Map<String, Object> resources = new LinkedHashMap<>();
    public Map<String, Object> production = new LinkedHashMap<>();
    public Map<String, Object> req = new LinkedHashMap<>();
    public List<String> place = new ArrayList<>();
    public Integer cost;
    /** Cards whose production box this card copied (Robotic Workforce, Cyberia Systems), in choice order. */
    public List<Copied> copied = new ArrayList<>();

    public static final class Copied {
        public String name;
        public Map<String, Object> production = new LinkedHashMap<>();
    }
}
