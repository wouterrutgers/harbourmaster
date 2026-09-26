package com.harbourmaster;

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

    @ConfigSection(name = "Dock", description = "Dock settings", position = 2)
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
