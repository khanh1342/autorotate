package com.autorotate.core;

import com.autorotate.AutoRotate;
import com.autorotate.config.Config;
import com.autorotate.solver.Aim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Executor {

    private static final int QUEUE_CAPACITY = 16;
    private static final int MAX_CLICK_AGE_TICKS = 60;

    private record QueuedClick(InteractionHand hand, BlockHitResult hit, long queuedAt) {
    }

    private static final Deque<QueuedClick> QUEUE = new ArrayDeque<>();
    private static RotationSession session;
    private static int holdTicks;
    private static BlockHitResult deferredHit;
    private static int deferrals;
    private static long clock;

    private Executor() {
    }

    public static RotationSession session() {
        return session;
    }

    public static long now() {
        return clock;
    }

    public static boolean isBusy() {
        return session != null || holdTicks > 0 || IceMelter.isActive() || BlockBreaker.isActive();
    }

    public static void enqueue(InteractionHand hand, BlockHitResult hit) {
        if (QUEUE.size() >= QUEUE_CAPACITY) {
            QUEUE.pollFirst();
        }
        QUEUE.add(new QueuedClick(hand, hit, clock));
    }

    public static void defer(InteractionHand hand, BlockHitResult hit, int ticks, int limit) {
        deferrals = hit == deferredHit ? deferrals + 1 : 1;
        deferredHit = hit;
        if (deferrals > limit) {
            Debug.log("Dropped the click at {} after {} attempts", hit.getBlockPos(), limit);
            return;
        }
        QUEUE.addFirst(new QueuedClick(hand, hit, clock));
        holdTicks = Math.max(holdTicks, ticks);
    }

    public static void submit(LocalPlayer player, PlannedAction action, Aim aim, boolean serverReady) {
        if (serverReady) {
            ServerRotation.runAt(player, () -> perform(player, action));
            return;
        }
        session = new RotationSession(player, action, aim);
        Debug.log("Aiming {} at {} for {}", aim, action.target(), action.item());
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            reset();
            ServerRotation.reset();
            return;
        }
        ServerRotation.tick();
        clock++;
        if (!Config.get().enabled) {
            cancel(player);
            return;
        }
        try {
            DiodeTuner.tick(minecraft.level, player);
            if (session != null) {
                advance(player);
            }
            if (session == null) {
                work(minecraft, player);
            }
        } catch (RuntimeException e) {
            AutoRotate.LOG.error("AutoRotate tick failed, dropping pending work", e);
            cancel(player);
        }
    }

    private static void work(Minecraft minecraft, LocalPlayer player) {
        if (BlockBreaker.isActive()) {
            BlockBreaker.tick(minecraft, player);
        } else if (IceMelter.isActive()) {
            IceMelter.tick(minecraft, player);
        } else if (holdTicks > 0) {
            holdTicks--;
        } else if (!QUEUE.isEmpty()) {
            drain(player);
        } else if (!Cooldown.sentThisTick()) {
            Stacker.tick(minecraft, player);
        }
    }

    private static void advance(LocalPlayer player) {
        switch (session.tick(player)) {
            case WAITING -> {
            }
            case READY -> fire(player);
            case FAILED -> {
                Debug.log("Gave up on {}: the rotation never settled", session.action().target());
                session.abort(player);
                session = null;
            }
            case FINISHED -> session = null;
        }
    }

    private static void fire(LocalPlayer player) {
        if (Cooldown.sentThisTick()) {
            return;
        }
        PlannedAction action = session.action();
        Hotbar.Supply supply = Hotbar.ensure(player, action.hand(), action.item());
        if (supply != Hotbar.Supply.HELD) {
            Debug.log("Postponed {}: {} was not in hand when the rotation settled", action.target(), action.item());
            session.abort(player);
            session = null;
            if (supply == Hotbar.Supply.PULLED) {
                defer(action.hand(), action.origin(), Config.get().swapSettleTicks, Placer.SWAP_ATTEMPTS);
            }
            return;
        }
        session.fire(player, () -> perform(player, action));
        if (session.isFinished()) {
            session = null;
        }
    }

    private static void perform(LocalPlayer player, PlannedAction action) {
        Actions.perform(player, action);
        if (action.hand() == InteractionHand.MAIN_HAND) {
            Hotbar.markUsed(player.getInventory().getSelectedSlot());
        }
        Cooldown.mark();
        if (!action.isFluid() && Config.get().adjustDiodes) {
            DiodeTuner.track(action.target(), action.hand());
        }
    }

    private static void drain(LocalPlayer player) {
        if (Cooldown.active()) {
            return;
        }
        while (!QUEUE.isEmpty()) {
            QueuedClick click = QUEUE.poll();
            if (isStale(player, click)) {
                Debug.log("Dropped a queued click at {}: too old or out of reach", click.hit().getBlockPos());
                continue;
            }
            InteractionResult result = Placer.decide(player, click.hand(), click.hit());
            if (result == null) {
                Actions.useOn(player, click.hand(), click.hit(), player.isShiftKeyDown());
                return;
            }
            if (result != InteractionResult.PASS) {
                return;
            }
        }
    }

    private static boolean isStale(LocalPlayer player, QueuedClick click) {
        return clock - click.queuedAt() > MAX_CLICK_AGE_TICKS
            || !player.isWithinBlockInteractionRange(click.hit().getBlockPos(), 0.0);
    }

    private static void cancel(LocalPlayer player) {
        if (session != null) {
            session.abort(player);
        }
        reset();
    }

    private static void reset() {
        BlockBreaker.cancel(Minecraft.getInstance());
        IceMelter.cancel();
        Stacker.clear();
        session = null;
        holdTicks = 0;
        deferredHit = null;
        deferrals = 0;
        QUEUE.clear();
        DiodeTuner.clear();
    }
}
