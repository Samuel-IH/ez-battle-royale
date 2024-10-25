package com.samuelih.ezroyale;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class GameState {
    private GamePhase phase = GamePhase.SETUP;
    private final List<BiConsumer<ServerLevel, GamePhase>> onPhaseChangeListeners = new ArrayList<>();

    public int loadTicks = 0;

    public void addPhaseChangeListener(BiConsumer<ServerLevel, GamePhase> listener) {
        onPhaseChangeListeners.add(listener);
    }

    public void setPhase(ServerLevel level, GamePhase newPhase) {
        phase = newPhase;
        onPhaseChangeListeners.forEach(listener -> listener.accept(level, newPhase));
    }

    public GamePhase getPhase() {
        return phase;
    }
}
