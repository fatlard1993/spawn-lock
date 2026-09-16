package justfatlard.spawn_lock;

import java.security.SecureRandom;
import java.util.HexFormat;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.server.level.ServerPlayer;

/**
 * The pass a player's game carries once they have been let in.
 *
 * <p>Left with the game through Pandorical's keepsakes, so it comes back at every login whatever
 * address the player arrives from, and a stranger who knows a name has nothing to show for it.
 * A game without Pandorical carries nothing, and is known by its address as before.
 */
final class Passes {
	private Passes() {}

	private static final String KEY = "spawn-lock:pass";
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Whether this player's game handed back the pass their name was given. */
	static boolean carried(ServerPlayer player, SpawnLockConfig config) {
		String expected = config.passes.get(player.getName().getString());
		return expected != null && expected.equals(PandoricalApi.keepsakes().get(player, KEY));
	}

	/** Make sure this player's game carries their pass, making one the first time. */
	static void hand(ServerPlayer player, SpawnLockConfig config) {
		String name = player.getName().getString();
		String pass = config.passes.get(name);
		if (pass == null) {
			byte[] bytes = new byte[16];
			RANDOM.nextBytes(bytes);
			pass = HexFormat.of().formatHex(bytes);
			config.passes.put(name, pass);
			config.save();
		}
		if (!pass.equals(PandoricalApi.keepsakes().get(player, KEY))) {
			PandoricalApi.keepsakes().put(player, KEY, pass);
		}
	}
}
