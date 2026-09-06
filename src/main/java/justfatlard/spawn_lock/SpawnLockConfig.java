package justfatlard.spawn_lock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The password and the people who have already said it.
 *
 * <p>One file, {@code config/spawn-lock.json}, written by hand or by {@code /spawnlock
 * set}. An empty password turns the door off, loudly, so a server that forgot to set one does
 * not quietly stand open. Remembered logins are name and address together: in offline mode a
 * name is a claim anyone can make, and the address is what makes it the same person coming
 * back rather than the same name. Anyone remembered walks straight in, for good by default;
 * a server that wants the word said again now and then sets how often the memory is wiped.
 */
public final class SpawnLockConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spawn-lock.json");
	/** The name this mod had before it learned to jail; read once if the new file is not there. */
	private static final Path OLD_FILE = FabricLoader.getInstance().getConfigDir().resolve("server-password.json");

	public String password = "";
	/** Seconds a newcomer has to say it before being shown out. */
	public int timeoutSeconds = 90;
	/** Wrong answers before being shown out. */
	public int attempts = 3;
	/** How far, in blocks, a newcomer may wander from where they arrived while at the door. */
	public int penRadius = 16;
	/** Days between wipes of the memory, so everyone is asked again; 0 never wipes it. */
	public int clearEveryDays = 0;
	/** When the memory was last wiped, epoch milliseconds. */
	public long lastCleared = 0;
	/** "name@address" to the epoch millisecond they were let in. */
	public Map<String, Long> remembered = new HashMap<>();
	/**
	 * Where each player standing at the door came from: dimension, x, y, z, yaw, pitch. Kept in
	 * the file rather than in memory because a player shown out mid-wait is saved wherever the
	 * door was, and this is what puts them back on their next visit.
	 */
	public Map<String, String> homes = new HashMap<>();
	/** Jailed players by name: the epoch millisecond they are freed, or -1 for until an operator says so. */
	public Map<String, Long> jails = new HashMap<>();

	public static SpawnLockConfig load() {
		try {
			Path source = Files.exists(FILE) ? FILE : Files.exists(OLD_FILE) ? OLD_FILE : null;
			if (source != null) {
				SpawnLockConfig read = GSON.fromJson(Files.readString(source), SpawnLockConfig.class);
				if (read != null) {
					if (read.remembered == null) read.remembered = new HashMap<>();
					if (read.homes == null) read.homes = new HashMap<>();
					if (read.jails == null) read.jails = new HashMap<>();
					if (source == OLD_FILE) read.save();
					return read;
				}
			}
		} catch (IOException | RuntimeException e) {
			Main.LOGGER.warn("Could not read {}: {}", FILE, e.getMessage());
		}
		SpawnLockConfig fresh = new SpawnLockConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException e) {
			Main.LOGGER.warn("Could not write {}: {}", FILE, e.getMessage());
		}
	}

	public boolean enabled() {
		return password != null && !password.isEmpty();
	}

	/** Whether this name is jailed right now; a served sentence is forgotten on the way. */
	public boolean isJailed(String name, long now) {
		Long until = jails.get(name);
		if (until == null) return false;
		if (until >= 0 && until <= now) {
			jails.remove(name);
			save();
			return false;
		}
		return true;
	}

	/** The address that stands for any address: a name an operator let in outright. */
	public static final String ANYWHERE = "*";

	public boolean isRemembered(String name, String address, long now) {
		wipeIfDue(now);
		return remembered.containsKey(name + "@" + address) || remembered.containsKey(name + "@" + ANYWHERE);
	}

	/** An operator lets a name in from anywhere, no password asked. */
	public void allow(String name, long now) {
		if (lastCleared == 0) lastCleared = now;
		remembered.put(name + "@" + ANYWHERE, now);
		save();
	}

	/** Forget every entry for this name, wherever it came from. @return whether there was one */
	public boolean forget(String name) {
		boolean any = remembered.keySet().removeIf(key -> key.startsWith(name + "@"));
		if (any) save();
		return any;
	}

	public void remember(String name, String address, long now) {
		if (lastCleared == 0) lastCleared = now;
		remembered.put(name + "@" + address, now);
		save();
	}

	/** Forget everyone, on the schedule if there is one. */
	public void wipeIfDue(long now) {
		if (clearEveryDays <= 0) return;
		if (lastCleared == 0) {
			lastCleared = now;
			save();
			return;
		}
		if (now - lastCleared < clearEveryDays * 86_400_000L) return;
		remembered.clear();
		lastCleared = now;
		save();
		Main.LOGGER.info("[spawn-lock] Memory wiped on schedule: everyone will be asked again");
	}
}
