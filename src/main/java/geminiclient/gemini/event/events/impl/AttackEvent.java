package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record AttackEvent(Player player, Entity entity) implements Event {}
