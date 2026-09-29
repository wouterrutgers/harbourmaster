package com.harbourmaster.model;

public enum DockGuidance {
    NONE(Target.NONE, "", "", false),
    DEPOSIT_CARGO(Target.CARGO_HOLD, "Deposit task cargo", "Put the carried crate in the cargo hold", false),
    BOARD_TO_DEPOSIT(Target.GANGPLANK, "Board to deposit cargo", "Put the carried crate in the cargo hold", false),
    TAKE_DELIVERY_CARGO(
            Target.CARGO_HOLD, "Take delivery crates", "Collect the highlighted cargo before going ashore", true),
    BOARD_TO_FETCH(Target.GANGPLANK, "Board to fetch crates", "Collect the remaining delivery cargo", true),
    LEAVE_TO_DELIVER(Target.GANGPLANK, "Go ashore to deliver cargo", "Take the carried crate to the ledger", true),
    DELIVER(Target.LEDGER, "Deliver task cargo", "Use the ledger to deliver the carried crate", true),
    LEAVE_TO_CLAIM(Target.GANGPLANK, "Go ashore to claim rewards", "Claim completed tasks from the port master", true),
    CLAIM_REWARDS(
            Target.PORT_MASTER,
            "Claim rewards from the port master",
            "Right click the port master and choose Claim rewards",
            true),
    LEAVE_TO_CHECK_BOARD(
            Target.GANGPLANK, "Go ashore to check offers", "Open the noticeboard before continuing", false),
    CHECK_BOARD(Target.NOTICEBOARD, "Check the noticeboard", "Accept the highlighted courier offers", false),
    LEAVE_TO_PICKUP(Target.GANGPLANK, "Go ashore to collect cargo", "Use the ledger to collect task cargo", false),
    PICKUP(Target.LEDGER, "Collect task cargo", "Use the ledger, then deposit cargo aboard", false),
    BOARD_TO_SAIL(Target.GANGPLANK, "Board to sail", "Continue along the route to the next port", false);

    public enum Target {
        NONE,
        CARGO_HOLD,
        GANGPLANK,
        NOTICEBOARD,
        LEDGER,
        PORT_MASTER
    }

    public final Target target;
    public final String instruction;
    public final String detail;
    public final boolean unload;

    DockGuidance(Target target, String instruction, String detail, boolean unload) {
        this.target = target;
        this.instruction = instruction;
        this.detail = detail;
        this.unload = unload;
    }

    public static DockGuidance next(
            HarbourmasterSnapshot state,
            boolean aboard,
            boolean carryingDelivery,
            boolean checkNoticeboard,
            boolean unloadCargo,
            boolean atSea) {
        if (state.depositCargo) {
            return aboard ? DEPOSIT_CARGO : BOARD_TO_DEPOSIT;
        }
        if (state.dock.port == null || atSea && state.dock.port != state.nextPort()) {
            return NONE;
        }
        if (carryingDelivery) {
            return aboard ? LEAVE_TO_DELIVER : DELIVER;
        }
        if (unloadCargo) {
            return TAKE_DELIVERY_CARGO;
        }
        if (state.dock.claimRewards) {
            return aboard ? LEAVE_TO_CLAIM : CLAIM_REWARDS;
        }
        if (checkNoticeboard || state.dock.hasAcceptance()) {
            return aboard ? LEAVE_TO_CHECK_BOARD : CHECK_BOARD;
        }
        if (state.dock.hasUnload()) {
            return aboard ? TAKE_DELIVERY_CARGO : BOARD_TO_FETCH;
        }
        if (state.dock.actions.stream().anyMatch(event -> event.action == RouteEvent.Action.PICKUP)) {
            return aboard ? LEAVE_TO_PICKUP : PICKUP;
        }
        if (state.sailingNext() && !aboard) {
            return BOARD_TO_SAIL;
        }
        return NONE;
    }
}
