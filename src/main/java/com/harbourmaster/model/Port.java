package com.harbourmaster.model;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ObjectID;

public enum Port {
    MUSA_POINT(
            DBTableID.SailingDock.Row.SAILING_DOCK_MUSA_POINT,
            "Musa Point",
            ObjectID.SAILING_GANGPLANK_MUSA_POINT,
            ObjectID.PORT_TASK_BOARD_MUSA_POINT,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_MUSA_POINT,
            new WorldPoint(2965, 3149, 0)),
    PORT_SARIM(
            DBTableID.SailingDock.Row.SAILING_DOCK_PORT_SARIM,
            "Port Sarim",
            ObjectID.SAILING_GANGPLANK_PORT_SARIM,
            ObjectID.PORT_TASK_BOARD_PORT_SARIM,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_SARIM,
            new WorldPoint(3056, 3194, 0)),
    PANDEMONIUM(
            DBTableID.SailingDock.Row.SAILING_DOCK_THE_PANDEMONIUM,
            "The Pandemonium",
            ObjectID.SAILING_GANGPLANK_THE_PANDEMONIUM,
            ObjectID.PORT_TASK_BOARD_PANDEMONIUM,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PANDEMONIUM,
            new WorldPoint(3078, 2987, 0)),
    ENTRANA(
            DBTableID.SailingDock.Row.SAILING_DOCK_ENTRANA,
            "Entrana",
            ObjectID.SAILING_GANGPLANK_ENTRANA,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_ENTRANA,
            new WorldPoint(2883, 3336, 0)),
    RUINS_OF_UNKAH(
            DBTableID.SailingDock.Row.SAILING_DOCK_RUINS_OF_UNKAH,
            "Ruins of Unkah",
            ObjectID.SAILING_GANGPLANK_RUINS_OF_UNKAH,
            ObjectID.PORT_TASK_BOARD_RUINS_OF_UNKAH,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_RUINS_OF_UNKAH,
            new WorldPoint(3140, 2824, 0)),
    RED_ROCK(
            DBTableID.SailingDock.Row.SAILING_DOCK_RED_ROCK,
            "Red Rock",
            ObjectID.SAILING_GANGPLANK_RED_ROCK,
            ObjectID.PORT_TASK_BOARD_RED_ROCK,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_RED_ROCK,
            new WorldPoint(2814, 2510, 0)),
    ARDOUGNE(
            DBTableID.SailingDock.Row.SAILING_DOCK_ARDOUGNE,
            "Ardougne",
            ObjectID.SAILING_GANGPLANK_ARDOUGNE,
            ObjectID.PORT_TASK_BOARD_ARDOUGNE,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_ARDOUGNE,
            new WorldPoint(2670, 3259, 0)),
    BRIMHAVEN(
            DBTableID.SailingDock.Row.SAILING_DOCK_BRIMHAVEN,
            "Brimhaven",
            ObjectID.SAILING_GANGPLANK_BRIMHAVEN,
            ObjectID.PORT_TASK_BOARD_BRIMHAVEN,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_BRIMHAVEN,
            new WorldPoint(2754, 3231, 0)),
    CATHERBY(
            DBTableID.SailingDock.Row.SAILING_DOCK_CATHERBY,
            "Catherby",
            ObjectID.SAILING_GANGPLANK_CATHERBY,
            ObjectID.PORT_TASK_BOARD_CATHERBY,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_CATHERBY,
            new WorldPoint(2796, 3407, 0)),
    PORT_KHAZARD(
            DBTableID.SailingDock.Row.SAILING_DOCK_PORT_KHAZARD,
            "Port Khazard",
            ObjectID.SAILING_GANGPLANK_PORT_KHAZARD,
            ObjectID.PORT_TASK_BOARD_PORT_KHAZARD,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_KHAZARD,
            new WorldPoint(2690, 3162, 0)),
    CORSAIR_COVE(
            DBTableID.SailingDock.Row.SAILING_DOCK_CORSAIR_COVE,
            "Corsair Cove",
            ObjectID.SAILING_GANGPLANK_CORSAIR_COVE,
            ObjectID.PORT_TASK_BOARD_CORSAIR_COVE,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_CORSAIR_COVE,
            new WorldPoint(2587, 2844, 0)),
    DEEPFIN_POINT(
            DBTableID.SailingDock.Row.SAILING_DOCK_DEEPFIN_POINT,
            "Deepfin Point",
            ObjectID.SAILING_GANGPLANK_DEEPFIN_POINT,
            ObjectID.PORT_TASK_BOARD_DEEPFIN_POINT,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_DEEPFIN_POINT,
            new WorldPoint(1923, 2752, 0)),
    SUNSET_COAST(
            DBTableID.SailingDock.Row.SAILING_DOCK_SUNSET_COAST,
            "Sunset Coast",
            ObjectID.SAILING_GANGPLANK_SUNSET_COAST,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_SUNSET_COAST,
            new WorldPoint(1506, 2971, 0)),
    ALDARIN(
            DBTableID.SailingDock.Row.SAILING_DOCK_ALDARIN,
            "Aldarin",
            ObjectID.SAILING_GANGPLANK_ALDARIN,
            ObjectID.PORT_TASK_BOARD_ALDARIN,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_ALDARIN,
            new WorldPoint(1454, 2977, 0)),
    SUMMER_SHORE(
            DBTableID.SailingDock.Row.SAILING_DOCK_THE_SUMMER_SHORE,
            "The Summer Shore",
            ObjectID.SAILING_GANGPLANK_THE_SUMMER_SHORE,
            ObjectID.PORT_TASK_BOARD_THE_SUMMER_SHORE,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_THE_SUMMER_SHORE,
            new WorldPoint(3174, 2362, 0)),
    VOID_KNIGHTS_OUTPOST(
            DBTableID.SailingDock.Row.SAILING_DOCK_VOID_KNIGHTS_OUTPOST,
            "Void Knights' Outpost",
            ObjectID.SAILING_GANGPLANK_VOID_KNIGHTS_OUTPOST,
            ObjectID.PORT_TASK_BOARD_VOID_KNIGHTS_OUTPOST,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_VOID_KNIGHTS_OUTPOST,
            new WorldPoint(2651, 2683, 0)),
    PORT_TYRAS(
            DBTableID.SailingDock.Row.SAILING_DOCK_PORT_TYRAS,
            "Port Tyras",
            ObjectID.SAILING_GANGPLANK_PORT_TYRAS,
            ObjectID.PORT_TASK_BOARD_PORT_TYRAS,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_TYRAS,
            new WorldPoint(2140, 3115, 0)),
    PORT_ROBERTS(
            DBTableID.SailingDock.Row.SAILING_DOCK_PORT_ROBERTS,
            "Port Roberts",
            ObjectID.SAILING_GANGPLANK_PORT_ROBERTS,
            ObjectID.PORT_TASK_BOARD_PORT_ROBERTS,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_ROBERTS,
            new WorldPoint(1857, 3307, 0)),
    LANDS_END(
            DBTableID.SailingDock.Row.SAILING_DOCK_LANDS_END,
            "Land's End",
            ObjectID.SAILING_GANGPLANK_LANDS_END,
            ObjectID.PORT_TASK_BOARD_LANDS_END,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_LANDS_END,
            new WorldPoint(1514, 3402, 0)),
    HOSIDIUS(
            DBTableID.SailingDock.Row.SAILING_DOCK_HOSIDIUS,
            "Hosidius",
            ObjectID.SAILING_GANGPLANK_HOSIDIUS,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_HOSIDIUS,
            new WorldPoint(1726, 3447, 0)),
    CIVITAS_ILLA_FORTIS(
            DBTableID.SailingDock.Row.SAILING_DOCK_CIVITAS_ILLA_FORTIS,
            "Civitas illa Fortis",
            ObjectID.SAILING_GANGPLANK_CIVITAS_ILLA_FORTIS,
            ObjectID.PORT_TASK_BOARD_CIVITAS_ILLA_FORTIS,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_CIVITAS_ILLA_FORTIS,
            new WorldPoint(1769, 3144, 0)),
    PORT_PISCARILIUS(
            DBTableID.SailingDock.Row.SAILING_DOCK_PORT_PISCARILIUS,
            "Port Piscarilius",
            ObjectID.SAILING_GANGPLANK_PORT_PISCARILIUS,
            ObjectID.PORT_TASK_BOARD_PORT_PISCARILIUS,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_PISCARILIUS,
            new WorldPoint(1845, 3681, 0)),
    CAIRN_ISLE(
            DBTableID.SailingDock.Row.SAILING_DOCK_CAIRN_ISLE,
            "Cairn Isle",
            ObjectID.SAILING_GANGPLANK_CAIRN_ISLE,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_CAIRN_ISLE,
            new WorldPoint(2745, 2952, 0)),
    PRIFDDINAS(
            DBTableID.SailingDock.Row.SAILING_DOCK_PRIFDDINAS,
            "Prifddinas",
            ObjectID.SAILING_GANGPLANK_PRIFDDINAS,
            ObjectID.PORT_TASK_BOARD_PRIFDDINAS,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PRIFDDINAS,
            new WorldPoint(2158, 3319, 0)),
    PISCATORIS(
            DBTableID.SailingDock.Row.SAILING_DOCK_PISCATORIS,
            "Piscatoris",
            ObjectID.SAILING_GANGPLANK_PISCATORIS,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_PISCATORIS,
            new WorldPoint(2300, 3689, 0)),
    LUNAR_ISLE(
            DBTableID.SailingDock.Row.SAILING_DOCK_LUNAR_ISLE,
            "Lunar Isle",
            ObjectID.SAILING_GANGPLANK_LUNAR_ISLE,
            ObjectID.PORT_TASK_BOARD_LUNAR_ISLE,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_LUNAR_ISLE,
            new WorldPoint(2157, 3881, 0)),
    RELLEKKA(
            DBTableID.SailingDock.Row.SAILING_DOCK_RELLEKKA,
            "Rellekka",
            ObjectID.SAILING_GANGPLANK_RELLEKKA,
            ObjectID.PORT_TASK_BOARD_RELLEKKA,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_RELLEKKA,
            new WorldPoint(2630, 3709, 0)),
    JATIZSO(
            DBTableID.SailingDock.Row.SAILING_DOCK_JATIZSO,
            "Jatizso",
            ObjectID.SAILING_GANGPLANK_JATIZSO,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_JATIZSO,
            new WorldPoint(2412, 3776, 0)),
    ETCETERIA(
            DBTableID.SailingDock.Row.SAILING_DOCK_ETCETERIA,
            "Etceteria",
            ObjectID.SAILING_GANGPLANK_ETCETERIA,
            ObjectID.PORT_TASK_BOARD_ETCETERIA,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_ETCETERIA,
            new WorldPoint(2612, 3836, 0)),
    NEITIZNOT(
            DBTableID.SailingDock.Row.SAILING_DOCK_NEITIZNOT,
            "Neitiznot",
            ObjectID.SAILING_GANGPLANK_NEITIZNOT,
            -1,
            ObjectID.DOCK_LOADING_BAY_LEDGER_TABLE_NEITIZNOT,
            new WorldPoint(2302, 3782, 0));

    public final int databaseRow;
    public final String name;
    public final int gangplankObject;
    public final int noticeboardObject;
    public final int ledgerObject;
    public final WorldPoint navigationLocation;

    Port(
            int databaseRow,
            String name,
            int gangplankObject,
            int noticeboardObject,
            int ledgerObject,
            WorldPoint navigationLocation) {
        this.databaseRow = databaseRow;
        this.name = name;
        this.gangplankObject = gangplankObject;
        this.noticeboardObject = noticeboardObject;
        this.ledgerObject = ledgerObject;
        this.navigationLocation = navigationLocation;
    }

    public static Port fromDatabaseRow(int row) {
        for (Port port : values()) {
            if (port.databaseRow == row) {
                return port;
            }
        }
        return null;
    }

    public static Port fromObject(int objectId) {
        if (objectId < 0) {
            return null;
        }
        for (Port port : values()) {
            if (port.noticeboardObject == objectId
                    || port.ledgerObject == objectId
                    || port.gangplankObject == objectId) {
                return port;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}
