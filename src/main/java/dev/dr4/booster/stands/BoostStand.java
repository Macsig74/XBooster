package dev.dr4.booster.stands;

import org.bukkit.Location;
import java.util.UUID;

public class BoostStand {

    public final String   boostId;
    public final Location location;

    public UUID itemDisplayId;
    public UUID textDisplayId;
    public UUID interactionId;

    public BoostStand(String boostId, Location location) {
        this.boostId  = boostId;
        this.location = location.clone();
    }
}
