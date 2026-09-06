package justfatlard.spawn_lock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Who is held at the spawn, why, and for how long.
 *
 * <p>Two reasons. A newcomer at the door, until they say the password in chat or their time
 * runs out. And a jailed player, until their sentence is served or an operator frees them.
 * Either way they are taken to the world spawn and penned there, drawn a wall only they can
 * see, unhurt, and unable to break, place, use or hit anything. Released, they go straight
 * back to wherever they were taken from.
 */
public final class Gate {
	private Gate() {}

	public enum Reason { DOOR, JAIL }

	/** @param until epoch millisecond the hold ends on its own; 0 for the door's own clock, -1 for never */
	private record Waiting(Reason reason, Vec3 pen, long since, long until, int wrong) {}

	private static final Map<UUID, Waiting> waiting = new HashMap<>();

	public static boolean isWaiting(ServerPlayer player) {
		return waiting.containsKey(player.getUUID());
	}

	public static boolean isAtDoor(ServerPlayer player) {
		Waiting at = waiting.get(player.getUUID());
		return at != null && at.reason() == Reason.DOOR;
	}

	/** A newcomer arrives: their spot written down, taken to the spawn, held there, told what to do. */
	public static void arrive(ServerPlayer player, SpawnLockConfig config) {
		hold(player, config, Reason.DOOR, 0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 100, 20));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
			Component.literal("Password required").withStyle(ChatFormatting.YELLOW)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
			Component.literal("Open chat (T) and type the password")));
		player.sendSystemMessage(Component.literal("This server needs a password. Open chat with T and type it, or /login <password>.")
			.withStyle(ChatFormatting.YELLOW));
	}

	/** A player is jailed: taken to the spawn and kept there until the time, or until freed. */
	public static void jail(ServerPlayer player, SpawnLockConfig config, long until) {
		hold(player, config, Reason.JAIL, until);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 100, 20));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
			Component.literal("Jailed").withStyle(ChatFormatting.RED)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
			Component.literal(until < 0 ? "Until an operator lets you out" : "For " + minutesLeft(until) + " more minute(s)")));
	}

	private static long minutesLeft(long until) {
		return Math.max(1, (until - System.currentTimeMillis() + 59_999L) / 60_000L);
	}

	private static void hold(ServerPlayer player, SpawnLockConfig config, Reason reason, long until) {
		net.minecraft.server.MinecraftServer server = player.level().getServer();
		var spawn = server.overworld().getRespawnData();
		net.minecraft.server.level.ServerLevel spawnLevel = server.getLevel(spawn.dimension());
		if (spawnLevel == null) spawnLevel = server.overworld();
		Vec3 pen = Vec3.atBottomCenterOf(spawn.globalPos().pos());

		// Only somebody who is not already in the pen has anywhere to go back to.
		boolean away = player.level() != spawnLevel
			|| player.position().distanceToSqr(pen) > (double) config.penRadius * config.penRadius;
		if (away) {
			if (!config.homes.containsKey(player.getName().getString())) {
				config.homes.put(player.getName().getString(), String.join(",",
					player.level().dimension().identifier().toString(),
					Double.toString(player.getX()), Double.toString(player.getY()), Double.toString(player.getZ()),
					Float.toString(player.getYRot()), Float.toString(player.getXRot())));
				config.save();
			}
			player.teleportTo(spawnLevel, pen.x, pen.y, pen.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		}

		waiting.put(player.getUUID(), new Waiting(reason, pen, System.currentTimeMillis(), until, 0));
		// Unseen at the door, so a stranger learns nothing about who is about; a jailed player
		// is somebody everyone knows, and is left visible to be laughed at.
		player.setInvisible(reason == Reason.DOOR);
		showPen(player, pen, config.penRadius);
	}

	/** Let a player out, wherever they were held for; false if they were not held. */
	public static boolean release(ServerPlayer player, SpawnLockConfig config) {
		Waiting at = waiting.remove(player.getUUID());
		if (at == null) return false;
		player.setInvisible(false);
		hidePen(player);
		sendHome(player, config);
		hidePen(player);
		return true;
	}

	/**
	 * The pen drawn as a wall: the world border, sent to this one player, sized to the pen. The
	 * client draws it, warns as it is approached and will not walk through it, and nobody else
	 * sees a thing. The real border goes back out when they are let in.
	 */
	private static void showPen(ServerPlayer player, Vec3 pen, int radius) {
		net.minecraft.world.level.border.WorldBorder wall = new net.minecraft.world.level.border.WorldBorder();
		wall.setCenter(pen.x, pen.z);
		wall.setSize(Math.max(2, radius) * 2.0);
		wall.setWarningBlocks(2);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(wall));
	}

	private static void hidePen(ServerPlayer player) {
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(
			player.level().getWorldBorder()));
	}

	/** Back to wherever they were before the door, if they were anywhere else. */
	public static void sendHome(ServerPlayer player, SpawnLockConfig config) {
		String home = config.homes.remove(player.getName().getString());
		if (home == null) return;
		config.save();
		try {
			String[] parts = home.split(",");
			var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
				net.minecraft.resources.Identifier.parse(parts[0]));
			net.minecraft.server.level.ServerLevel level = player.level().getServer().getLevel(key);
			if (level == null) return;
			player.teleportTo(level, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
				java.util.Set.of(), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]), false);
		} catch (RuntimeException e) {
			Main.LOGGER.warn("Could not send {} home ({}): {}", player.getName().getString(), home, e.getMessage());
		}
	}

	/** Somebody said something at the door. @return whether the door opened */
	public static boolean answer(ServerPlayer player, String said, SpawnLockConfig config) {
		Waiting at = waiting.get(player.getUUID());
		if (at == null || at.reason() != Reason.DOOR) return true;
		if (said.trim().equals(config.password)) {
			admit(player, config);
			return true;
		}
		int wrong = at.wrong() + 1;
		if (wrong >= config.attempts) {
			player.connection.disconnect(Component.literal("Wrong password."));
			return false;
		}
		waiting.put(player.getUUID(), new Waiting(at.reason(), at.pen(), at.since(), at.until(), wrong));
		player.sendSystemMessage(Component.literal("Not it. " + (config.attempts - wrong) + " more try"
			+ (config.attempts - wrong == 1 ? "" : "ies") + ".").withStyle(ChatFormatting.RED));
		return false;
	}

	private static void admit(ServerPlayer player, SpawnLockConfig config) {
		config.remember(player.getName().getString(), addressOf(player), System.currentTimeMillis());
		release(player, config);
		player.sendSystemMessage(Component.literal("Welcome in.").withStyle(ChatFormatting.GREEN));
		Main.LOGGER.info("{} gave the password", player.getName().getString());
	}

	public static void leave(ServerPlayer player) {
		waiting.remove(player.getUUID());
	}

	/** Every tick: hold everyone at the door where they are, and show out anyone who has waited too long. */
	public static void tick(net.minecraft.server.MinecraftServer server, SpawnLockConfig config) {
		if (waiting.isEmpty()) return;
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Waiting at = waiting.get(player.getUUID());
			if (at == null) continue;
			if (at.reason() == Reason.DOOR) {
				if (now - at.since() > config.timeoutSeconds * 1000L) {
					player.connection.disconnect(Component.literal("No password given."));
					continue;
				}
				if (server.getTickCount() % 20 == 0) {
					long left = config.timeoutSeconds - (now - at.since()) / 1000L;
					player.sendOverlayMessage(Component.literal("Press T and type the password  -  " + left + "s")
						.withStyle(ChatFormatting.YELLOW));
				}
			} else {
				if (at.until() >= 0 && now >= at.until()) {
					config.jails.remove(player.getName().getString());
					config.save();
					release(player, config);
					player.sendSystemMessage(Component.literal("Time served.").withStyle(ChatFormatting.GREEN));
					continue;
				}
				if (server.getTickCount() % 20 == 0) {
					player.sendOverlayMessage(Component.literal(at.until() < 0
						? "Jailed until an operator lets you out"
						: "Jailed  -  " + minutesLeft(at.until()) + " min left").withStyle(ChatFormatting.RED));
				}
			}
			// The pen: as far as they may wander from the spawn. Sixteen by default,
			// vanilla's own spawn protection radius, inside which an ordinary player could not
			// change anything even if this mod let them.
			// The wall, again, once a second: the join sequence and a content sync both hand the
			// client a border of their own after ours, and the last one sent is the one drawn.
			if (server.getTickCount() % 20 == 0) showPen(player, at.pen(), config.penRadius);
			// Unhurt is refused in Main; unbothered is here: no burning screen, no air running out.
			if (player.isOnFire()) player.clearFire();
			if (player.getAirSupply() < player.getMaxAirSupply()) player.setAirSupply(player.getMaxAirSupply());
			// Square, like the wall they can see.
			double reach = Math.max(2, config.penRadius);
			if (Math.abs(player.getX() - at.pen().x) > reach || Math.abs(player.getZ() - at.pen().z) > reach) {
				player.teleportTo(at.pen().x, at.pen().y, at.pen().z);
			}
		}
	}

	public static String addressOf(ServerPlayer player) {
		String raw = player.connection.getRemoteAddress() == null ? "?" : player.connection.getRemoteAddress().toString();
		// "/1.2.3.4:5678" or "/[::1]:5678": the address without the slash and the port.
		int colon = raw.lastIndexOf(':');
		String host = colon > 0 ? raw.substring(0, colon) : raw;
		return host.replace("/", "");
	}
}
