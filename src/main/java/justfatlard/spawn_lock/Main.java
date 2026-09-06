package justfatlard.spawn_lock;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A lock on the spawn.
 *
 * <p>Two uses of one pen. A password at the door, for a server on a public address that runs
 * in offline mode, where a name is a claim and a whitelist is a list of claims: the password
 * is said in chat, so a stock client can say it, and until it is said the newcomer is held at
 * the spawn and can touch nothing. And a jail, for an operator to put somebody in the same pen
 * for a while, or until let out.
 */
public class Main implements ModInitializer {
	public static final String MOD_ID = "spawn-lock-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger("spawn-lock");

	public static SpawnLockConfig config = new SpawnLockConfig();

	@Override
	public void onInitialize() {
		config = SpawnLockConfig.load();
		if (!config.enabled()) {
			LOGGER.warn("No password set: the door is open. Set one in config/spawn-lock.json or with /spawnlock set");
		}

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (!config.enabled()) {
				ServerPlayer player = handler.getPlayer();
				if (config.isJailed(player.getName().getString(), System.currentTimeMillis())) {
					Gate.jail(player, config, config.jails.get(player.getName().getString()));
				}
				return;
			}
			ServerPlayer player = handler.getPlayer();
			long now = System.currentTimeMillis();
			// A sentence outlives a relog.
			if (config.isJailed(player.getName().getString(), now)) {
				Gate.jail(player, config, config.jails.get(player.getName().getString()));
				return;
			}
			if (config.isRemembered(player.getName().getString(), Gate.addressOf(player), now)) {
				// Shown out mid-wait last time and remembered since: they are saved at the door.
				Gate.sendHome(player, config);
				return;
			}
			Gate.arrive(player, config);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Gate.leave(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Gate.tick(server, config);
			if (server.getTickCount() % 1200 == 0) config.wipeIfDue(System.currentTimeMillis());
		});

		// What is said at the door is the password, or an attempt at it, and never chat.
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!Gate.isAtDoor(sender)) return true;
			Gate.answer(sender, message.signedContent(), config);
			return false;
		});

		// Nothing done at the door counts.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> held(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !held(player));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && Gate.isWaiting(player)));

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("jail")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
					.executes(context -> jail(context.getSource(),
						net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"), -1))
					.then(Commands.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
						.executes(context -> jail(context.getSource(),
							net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"),
							System.currentTimeMillis() + 60_000L * com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "minutes"))))));
			dispatcher.register(Commands.literal("unjail")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.argument("player", StringArgumentType.word())
					.executes(context -> unjail(context.getSource(), StringArgumentType.getString(context, "player")))));
			dispatcher.register(Commands.literal("login")
				.then(Commands.argument("password", StringArgumentType.greedyString())
					.executes(context -> {
						ServerPlayer player = context.getSource().getPlayerOrException();
						if (!Gate.isWaiting(player)) {
							context.getSource().sendSuccess(() -> Component.literal("You are already in."), false);
							return 1;
						}
						return Gate.answer(player, StringArgumentType.getString(context, "password"), config) ? 1 : 0;
					})));
			dispatcher.register(Commands.literal("spawnlock")
				.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
				.then(Commands.literal("set")
					.then(Commands.argument("password", StringArgumentType.greedyString())
						.executes(context -> {
							config.password = StringArgumentType.getString(context, "password").trim();
							config.remembered.clear();
							config.save();
							context.getSource().sendSuccess(() -> Component.literal("Password set; everyone will be asked again.")
								.withStyle(ChatFormatting.GREEN), true);
							return 1;
						})))
				.then(Commands.literal("forget")
					.executes(context -> {
						config.remembered.clear();
						config.save();
						context.getSource().sendSuccess(() -> Component.literal("Everyone will be asked again."), true);
						return 1;
					}))
				// For a file edited by hand while the server runs: read it again rather than
				// have the next save overwrite it with what memory held.
				.then(Commands.literal("reload")
					.executes(context -> {
						config = SpawnLockConfig.load();
						int known = config.remembered.size();
						context.getSource().sendSuccess(() -> Component.literal("Reloaded: " + known + " remembered, "
							+ config.jails.size() + " jailed, door " + (config.enabled() ? "closed" : "open")), true);
						return 1;
					}))
				// Let a name in from anywhere, no password: for the household and the friends
				// you would have told the word to anyway.
				.then(Commands.literal("allow")
					.then(Commands.argument("player", StringArgumentType.word())
						.executes(context -> {
							String name = StringArgumentType.getString(context, "player");
							config.allow(name, System.currentTimeMillis());
							ServerPlayer waiting = context.getSource().getServer().getPlayerList().getPlayerByName(name);
							if (waiting != null && Gate.isAtDoor(waiting)) {
								Gate.release(waiting, config);
								waiting.sendSystemMessage(Component.literal("Welcome in.").withStyle(ChatFormatting.GREEN));
							}
							context.getSource().sendSuccess(() -> Component.literal(name + " may come in without the password."), true);
							return 1;
						})))
				.then(Commands.literal("disallow")
					.then(Commands.argument("player", StringArgumentType.word())
						.executes(context -> {
							String name = StringArgumentType.getString(context, "player");
							boolean known = config.forget(name);
							context.getSource().sendSuccess(() -> Component.literal(known
								? name + " will be asked for the password again." : name + " was not on the list."), true);
							return known ? 1 : 0;
						})))
				.then(Commands.literal("off")
					.executes(context -> {
						config.password = "";
						config.save();
						context.getSource().sendSuccess(() -> Component.literal("Password removed: the door is open.")
							.withStyle(ChatFormatting.RED), true);
						return 1;
					})));
		});

		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pandorical")) MenuPage.register();

		LOGGER.info("[spawn-lock] Loaded ({})", config.enabled() ? "door closed" : "no password set");
	}

	private static int jail(net.minecraft.commands.CommandSourceStack source, ServerPlayer player, long until) {
		if (Gate.isWaiting(player) && !Gate.isAtDoor(player)) {
			source.sendFailure(Component.literal(player.getName().getString() + " is already jailed."));
			return 0;
		}
		config.jails.put(player.getName().getString(), until);
		config.save();
		Gate.release(player, config);
		Gate.jail(player, config, until);
		String term = until < 0 ? "until let out" : "for " + ((until - System.currentTimeMillis() + 59_999L) / 60_000L) + " minute(s)";
		source.sendSuccess(() -> Component.literal("Jailed " + player.getName().getString() + " " + term + "."), true);
		LOGGER.info("[spawn-lock] {} jailed {} {}", source.getTextName(), player.getName().getString(), term);
		return 1;
	}

	private static int unjail(net.minecraft.commands.CommandSourceStack source, String name) {
		boolean known = config.jails.remove(name) != null;
		config.save();
		ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
		boolean freed = player != null && !Gate.isAtDoor(player) && Gate.release(player, config);
		if (freed) player.sendSystemMessage(Component.literal("You have been let out.").withStyle(ChatFormatting.GREEN));
		if (!known && !freed) {
			source.sendFailure(Component.literal(name + " is not jailed."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Freed " + name + "."), true);
		return 1;
	}

	private static boolean held(net.minecraft.world.entity.player.Player player) {
		return player instanceof ServerPlayer serverPlayer && Gate.isWaiting(serverPlayer);
	}
}
