package justfatlard.spawn_lock;

import java.util.LinkedHashMap;
import java.util.Map;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * The door's memory on the mod's page of the Pandorical menu, for operators.
 *
 * <p>Everyone who may walk in without the password, one line each, with the address they are
 * known from or "anywhere" for a name an operator let in outright, and a button to forget
 * them. Adding stays with the commands, which know how to spell a name.
 */
final class MenuPage {
	private MenuPage() {}

	static void register() {
		PandoricalApi.settings().group(Main.MOD_ID, "Spawn Lock")
			.list("allowed", "Allowed players",
				MenuPage::entries,
				(player, id) -> {
					Main.config.forgetEntry(id);
				})
			.describe("Come in without the password: the household, and anyone an operator let in with /spawnlock allow")
			.shownWhen(MenuPage::isOperator);
	}

	private static Map<String, String> entries(ServerPlayer player) {
		Map<String, String> named = new LinkedHashMap<>();
		for (String key : Main.config.remembered.keySet()) {
			int at = key.lastIndexOf('@');
			String name = at > 0 ? key.substring(0, at) : key;
			String from = at > 0 ? key.substring(at + 1) : "?";
			named.put(key, name + (SpawnLockConfig.ANYWHERE.equals(from) ? "  -  anywhere" : "  -  from " + from));
		}
		return named;
	}

	private static boolean isOperator(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.createCommandSourceStack().permissions());
	}
}
