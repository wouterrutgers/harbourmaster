package com.harbourmaster.tracker;

import com.harbourmaster.model.ActiveTask;
import com.harbourmaster.model.CourierTask;
import com.harbourmaster.model.Port;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

public final class CargoTracker {
    public boolean needsDeposit(Client client, Map<Integer, Destination> destinations, Port dock) {
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        Item carried = equipment == null ? null : equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        Destination destination = carried == null ? null : destinations.get(carried.getId());
        return destination != null && destination.port != dock;
    }

    public Map<Integer, Destination> destinations(List<ActiveTask> tasks, Port dock) {
        Map<Integer, Destination> result = new HashMap<>();
        for (ActiveTask active : tasks) {
            if (active.definition == null || active.isFinished()) {
                continue;
            }
            CourierTask task = active.definition;
            result.put(task.itemId, new Destination(task.delivery, task.delivery == dock && active.carried() > 0));
        }
        return Map.copyOf(result);
    }

    public static final class Destination {
        public final Port port;
        public final boolean unload;

        private Destination(Port port, boolean unload) {
            this.port = port;
            this.unload = unload;
        }
    }
}
