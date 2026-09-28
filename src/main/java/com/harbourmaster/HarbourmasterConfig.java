package com.harbourmaster;

import com.harbourmaster.model.BoatFocus;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("harbourmaster")
public interface HarbourmasterConfig extends Config {
    @ConfigSection(name = "Noticeboard", description = "Noticeboard settings", position = 0)
    String noticeboard = "noticeboard";

    @ConfigItem(
            keyName = "highlightNoticeboards",
            name = "Highlight noticeboards",
            description = "Highlight nearby Sailing task boards.",
            section = noticeboard,
            position = 0)
    default boolean highlightNoticeboards() {
        return true;
    }

    @ConfigItem(
            keyName = "subdueFullBoards",
            name = "Subdue full boards",
            description = "Use a subdued outline when no task slots are free.",
            section = noticeboard,
            position = 1)
    default boolean subdueFullBoards() {
        return true;
    }

    @ConfigItem(
            keyName = "rankOffers",
            name = "Highlight recommended offers",
            description = "Highlight courier offers selected for the best courier XP per hour.",
            section = noticeboard,
            position = 2)
    default boolean rankOffers() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "bestOfferColor",
            name = "Recommended offer colour",
            description = "Colour for recommended courier jobs and nearby task boards.",
            section = noticeboard,
            position = 3)
    default Color bestOfferColor() {
        return new Color(70, 225, 175);
    }

    @ConfigSection(name = "Route", description = "Route settings", position = 1)
    String route = "route";

    @ConfigItem(
            keyName = "enableOptimizer",
            name = "Enable route optimiser",
            description = "Find an efficient sailing route through your courier plan.",
            section = route,
            position = 0)
    default boolean enableOptimizer() {
        return true;
    }

    @ConfigItem(
            keyName = "showRouteOverlay",
            name = "Show route status",
            description = "Show the next destination and action while sailing.",
            section = route,
            position = 1)
    default boolean showRouteOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "showWorldMap",
            name = "Show world map route",
            description = "Draw the route on the world map.",
            section = route,
            position = 2)
    default boolean showWorldMap() {
        return true;
    }

    @ConfigItem(
            keyName = "showWorldRoute",
            name = "Show in-world route",
            description = "Draw the current sailing leg in the world.",
            section = route,
            position = 3)
    default boolean showWorldRoute() {
        return true;
    }

    @ConfigItem(
            keyName = "showMinimap",
            name = "Show minimap route",
            description = "Draw the current sailing leg on the minimap.",
            section = route,
            position = 4)
    default boolean showMinimap() {
        return true;
    }

    @ConfigItem(
            keyName = "showCompleteRoute",
            name = "Show complete route",
            description = "Also draw future legs in a subdued colour.",
            section = route,
            position = 5)
    default boolean showCompleteRoute() {
        return false;
    }

    @Alpha
    @ConfigItem(
            keyName = "activeRouteColor",
            name = "Active route colour",
            description = "Colour of the current sailing leg.",
            section = route,
            position = 6)
    default Color activeRouteColor() {
        return new Color(70, 225, 175);
    }

    @Alpha
    @ConfigItem(
            keyName = "futureRouteColor",
            name = "Future route colour",
            description = "Colour of future sailing legs.",
            section = route,
            position = 7)
    default Color futureRouteColor() {
        return new Color(120, 145, 165, 130);
    }

    @ConfigSection(
            name = "Teleports and charter ships",
            description = "Enable the methods you can use. Required gear, supplies and unlocks are assumed available.",
            position = 2)
    String travel = "travel";

    @ConfigItem(
            keyName = "useTeleports",
            name = "Use teleports and charter ships",
            description = "Include the travel methods enabled below when planning courier routes.",
            section = travel,
            position = 0)
    default boolean useTeleports() {
        return false;
    }

    @ConfigItem(
            keyName = "sailorsAmuletPandemonium",
            name = "Sailors' amulet to Pandemonium",
            description = "Assume you can use the Sailors' amulet teleport to Pandemonium whenever needed.",
            section = travel,
            position = 1)
    default boolean sailorsAmuletPandemonium() {
        return false;
    }

    @ConfigItem(
            keyName = "sailorsAmuletDeepfinPoint",
            name = "Sailors' amulet to Deepfin Point",
            description = "Assume you can use the Sailors' amulet teleport to Deepfin Point whenever needed.",
            section = travel,
            position = 2)
    default boolean sailorsAmuletDeepfinPoint() {
        return false;
    }

    @ConfigItem(
            keyName = "sailorsAmuletPortRoberts",
            name = "Sailors' amulet to Port Roberts",
            description = "Assume you can use the Sailors' amulet teleport to Port Roberts whenever needed.",
            section = travel,
            position = 3)
    default boolean sailorsAmuletPortRoberts() {
        return false;
    }

    @ConfigItem(
            keyName = "aldarinTeleport",
            name = "Aldarin house portal",
            description = "Assume you can reach the Aldarin house portal using a spell, tablet or Construction cape.",
            section = travel,
            position = 4)
    default boolean aldarinTeleport() {
        return false;
    }

    @ConfigItem(
            keyName = "prifddinasTeleport",
            name = "Prifddinas house portal",
            description =
                    "Assume you can reach the Prifddinas house portal using a spell, tablet or Construction cape.",
            section = travel,
            position = 5)
    default boolean prifddinasTeleport() {
        return false;
    }

    @ConfigItem(
            keyName = "teleportCrystal",
            name = "Teleport crystal to Prifddinas",
            description = "Assume you can teleport to Prifddinas using a charged or eternal teleport crystal.",
            section = travel,
            position = 6)
    default boolean teleportCrystal() {
        return false;
    }

    @ConfigItem(
            keyName = "moonclanTeleport",
            name = "Moonclan teleport",
            description = "Assume you can use a Moonclan teleport spell or tablet whenever needed.",
            section = travel,
            position = 7)
    default boolean moonclanTeleport() {
        return false;
    }

    @ConfigItem(
            keyName = "lunarIsleScroll",
            name = "Lunar Isle scroll",
            description = "Assume you can use a Lunar Isle teleport scroll whenever needed.",
            section = travel,
            position = 8)
    default boolean lunarIsleScroll() {
        return false;
    }

    @ConfigItem(
            keyName = "teleportToBoat",
            name = "Teleport to boat",
            description = "Use the spell or tablet to reach boats configured with a greater teleport focus.",
            section = travel,
            position = 9)
    default boolean teleportToBoat() {
        return false;
    }

    @ConfigItem(
            keyName = "summonBoat",
            name = "Summon boat",
            description = "Use the spell or tablet to summon your courier boat when configured with either focus.",
            section = travel,
            position = 10)
    default boolean summonBoat() {
        return false;
    }

    @ConfigItem(
            keyName = "charterShips",
            name = "Charter ships",
            description = "Assume all charter routes are unlocked and you have enough coins for each journey.",
            section = travel,
            position = 11)
    default boolean charterShips() {
        return false;
    }

    @ConfigSection(name = "Boat", description = "Choose the teleport focus fitted to each boat.", position = 3)
    String boat = "boat";

    @ConfigItem(
            keyName = "boat1Focus",
            name = "Boat 1",
            description = "A teleport focus allows summoning. A greater focus also allows teleporting to this boat.",
            section = boat,
            position = 0)
    default BoatFocus boat1Focus() {
        return BoatFocus.NONE;
    }

    @ConfigItem(
            keyName = "boat2Focus",
            name = "Boat 2",
            description = "A teleport focus allows summoning. A greater focus also allows teleporting to this boat.",
            section = boat,
            position = 1)
    default BoatFocus boat2Focus() {
        return BoatFocus.NONE;
    }

    @ConfigItem(
            keyName = "boat3Focus",
            name = "Boat 3",
            description = "A teleport focus allows summoning. A greater focus also allows teleporting to this boat.",
            section = boat,
            position = 2)
    default BoatFocus boat3Focus() {
        return BoatFocus.NONE;
    }

    @ConfigItem(
            keyName = "boat4Focus",
            name = "Boat 4",
            description = "A teleport focus allows summoning. A greater focus also allows teleporting to this boat.",
            section = boat,
            position = 3)
    default BoatFocus boat4Focus() {
        return BoatFocus.NONE;
    }

    @ConfigItem(
            keyName = "boat5Focus",
            name = "Boat 5",
            description = "A teleport focus allows summoning. A greater focus also allows teleporting to this boat.",
            section = boat,
            position = 4)
    default BoatFocus boat5Focus() {
        return BoatFocus.NONE;
    }

    @ConfigSection(name = "Dock", description = "Dock settings", position = 4)
    String dock = "dock";

    @ConfigItem(
            keyName = "highlightLedger",
            name = "Highlight ledger",
            description = "Highlight the ledger when cargo can be picked up or delivered.",
            section = dock,
            position = 0)
    default boolean highlightLedger() {
        return true;
    }

    @ConfigItem(
            keyName = "highlightCargoHold",
            name = "Highlight cargo hold",
            description = "Highlight your boarded boat’s cargo hold for dock actions and carried cargo to deposit.",
            section = dock,
            position = 1)
    default boolean highlightCargoHold() {
        return true;
    }

    @ConfigItem(
            keyName = "highlightGangplank",
            name = "Highlight gangplank",
            description = "Highlight the gangplank when boarding for cargo or going ashore for dock actions.",
            section = dock,
            position = 7)
    default boolean highlightGangplank() {
        return true;
    }

    @ConfigItem(
            keyName = "highlightUnloadCrates",
            name = "Highlight unload crates",
            description = "Strongly highlight only cargo destined for the current port.",
            section = dock,
            position = 2)
    default boolean highlightUnloadCrates() {
        return true;
    }

    @ConfigItem(
            keyName = "showCargoDestinations",
            name = "Show cargo destinations",
            description = "Label active task crates in both cargo interfaces and inventory.",
            section = dock,
            position = 3)
    default boolean showCargoDestinations() {
        return true;
    }

    @ConfigItem(
            keyName = "showDockChecklist",
            name = "Show dock checklist",
            description = "Group all available loads and unloads at the current port.",
            section = dock,
            position = 4)
    default boolean showDockChecklist() {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "loadColor",
            name = "Load colour",
            description = "Colour for cargo pickup actions.",
            section = dock,
            position = 5)
    default Color loadColor() {
        return new Color(100, 180, 255);
    }

    @Alpha
    @ConfigItem(
            keyName = "unloadColor",
            name = "Unload colour",
            description = "Colour for cargo delivery actions.",
            section = dock,
            position = 6)
    default Color unloadColor() {
        return new Color(70, 225, 175);
    }
}
