package net.eowyn.emptyserverkick;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EmptyServerKick implements ModInitializer {
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private MinecraftServer server;
  private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStart);
    ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
    ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerLeave);
  }

  private void onServerStart(MinecraftServer minecraftServer) {
    this.server = minecraftServer;
  }

  private void onPlayerJoin(ServerPlayNetworkHandler serverPlayNetworkHandler, PacketSender packetSender, MinecraftServer minecraftServer) {
    if (this.playerCount() == 0) {
      startKickTimer(serverPlayNetworkHandler.getPlayer());
    }
  }

  private void onPlayerLeave(ServerPlayNetworkHandler serverPlayNetworkHandler, MinecraftServer minecraftServer) {
    int players = this.playerCount();

    if (players == 1) {
      this.cancelFutures();
    } else if (players > 1) {
      this.startKickTimer(serverPlayNetworkHandler.getPlayer());
    }
  }

  private void startKickTimer(ServerPlayerEntity player) {
    logText("Server shutting down in 10 minutes as only one player is online", player);

    ScheduledFuture<?> future = scheduler.schedule(() -> {
      if (this.playerCount() == 1) {
        logText("Server shutting down in 1 minute", player);

        ScheduledFuture<?> futureInner = scheduler.schedule(() -> {
          if (this.playerCount() == 1) {
            logText("Server shutting down...", player);
            server.getPlayerManager().disconnectAllPlayers();
          }
        }, 1, TimeUnit.MINUTES);

        this.cancelFutures();
        this.scheduledFutures.add(futureInner);
      }
    }, 9, TimeUnit.MINUTES);

    this.cancelFutures();
    this.scheduledFutures.add(future);
  }

  private void cancelFutures() {
    this.scheduledFutures.forEach((f) -> {
      f.cancel(true);
    });
    this.scheduledFutures.clear();
  }

  private int playerCount() {
    return server.getPlayerManager().getPlayerList().size();
  }

  private void logText(String string, ServerPlayerEntity player) {
    player.sendMessage(Text.of(string));
    server.sendMessage(Text.of(string));
  }
}
