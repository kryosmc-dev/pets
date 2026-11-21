package com.kryos.mascotas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Ageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PetManager {

    private final KryosMascotas plugin;
    private final NamespacedKey petKey;
    private final File file;

    private final Map<UUID, PlayerPets> data = new HashMap<>();
    private final Map<UUID, Mob> active = new HashMap<>();
    private final Map<UUID, LivingEntity> targets = new HashMap<>();
    private final Map<UUID, UUID> lastHit = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> attackCd = new HashMap<>();
    private int tick = 0;
    private final Map<UUID, String> renaming = new HashMap<>();

    public PetManager(KryosMascotas plugin) {
        this.plugin = plugin;
        this.petKey = new NamespacedKey(plugin, "mascota_duena");
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ config

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public ConfigurationSection petSec(String id) {
        return cfg().getConfigurationSection("mascotas." + id);
    }

    public Set<String> petIds() {
        ConfigurationSection s = cfg().getConfigurationSection("mascotas");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public int maxLevel() {
        return cfg().getInt("maximo-nivel", 50);
    }

    public double xpNeeded(int level) {
        return cfg().getDouble("xp.base", 50) + level * cfg().getDouble("xp.por-nivel", 30);
    }

    public double damage(String petId, int level) {
        ConfigurationSection sec = petSec(petId);
        return sec.getDouble("danio-base", 3) + sec.getDouble("danio-por-nivel", 0.25) * (level - 1);
    }

    public static int num(Map<?, ?> m, String k, int def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.intValue() : def;
    }

    public static double dbl(Map<?, ?> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String abilityName(String tipo) {
        return switch (tipo.toUpperCase()) {
            case "FUEGO" -> "Golpe de fuego";
            case "RAYO" -> "Rayo";
            case "LENTITUD" -> "Golpe helado";
            case "VENENO" -> "Golpe venenoso";
            case "CURAR" -> "Sanación";
            case "ESCUDO" -> "Escudo protector";
            default -> tipo;
        };
    }

    // -------------------------------------------------------------------- data

    public void load() {
        data.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection jugadores = y.getConfigurationSection("jugadores");
        if (jugadores == null) return;
        for (String key : jugadores.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection s = jugadores.getConfigurationSection(key);
            if (s == null) continue;
            PlayerPets pp = new PlayerPets();
            pp.unlocked.addAll(s.getStringList("desbloqueadas"));
            pp.active = s.getString("activa");
            ConfigurationSection ms = s.getConfigurationSection("mascotas");
            if (ms != null) {
                for (String pid : ms.getKeys(false)) {
                    PlayerPets.Progress pr = new PlayerPets.Progress();
                    pr.level = ms.getInt(pid + ".nivel", 1);
                    pr.xp = ms.getDouble(pid + ".xp", 0);
                    pr.particle = ms.getString(pid + ".estela", "auto");
                    pr.claimed.addAll(ms.getIntegerList(pid + ".reclamados"));
                    pr.kills = ms.getInt(pid + ".kills", 0);
                    pr.nickname = ms.getString(pid + ".apodo");
                    pp.progress.put(pid, pr);
                }
            }
            data.put(id, pp);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerPets> en : data.entrySet()) {
            String base = "jugadores." + en.getKey();
            PlayerPets pp = en.getValue();
            y.set(base + ".desbloqueadas", new ArrayList<>(pp.unlocked));
            y.set(base + ".activa", pp.active);
            for (Map.Entry<String, PlayerPets.Progress> pe : pp.progress.entrySet()) {
                String pb = base + ".mascotas." + pe.getKey();
                PlayerPets.Progress pr = pe.getValue();
                y.set(pb + ".nivel", pr.level);
                y.set(pb + ".xp", pr.xp);
                y.set(pb + ".estela", pr.particle);
                y.set(pb + ".reclamados", new ArrayList<>(pr.claimed));
                y.set(pb + ".kills", pr.kills);
                y.set(pb + ".apodo", pr.nickname);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + ex.getMessage());
        }
    }

    public PlayerPets data(UUID id) {
        PlayerPets pp = data.computeIfAbsent(id, k -> new PlayerPets());
        for (String pid : petIds()) {
            if (petSec(pid).getBoolean("por-defecto", false)) pp.unlocked.add(pid);
        }
        return pp;
    }

    // ------------------------------------------------------------------ tareas

    public void startTasks() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPets, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrails, 4L, 4L);
        long mins = Math.max(1, cfg().getInt("autoguardado-minutos", 5));
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, mins * 1200L, mins * 1200L);
    }

    public void cleanup() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (isPet(e)) e.remove();
            }
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) despawn(id);
        save();
    }

    private void tickPets() {
        tick++;
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player owner = Bukkit.getPlayer(id);
            if (owner == null || !owner.isOnline()) {
                despawn(id);
                continue;
            }
            PlayerPets pp = data(id);
            String petId = pp.active;
            if (petId == null || petSec(petId) == null) {
                despawn(id);
                continue;
            }
            Mob pet = active.get(id);
            if (pet == null || !pet.isValid()) {
                spawn(owner, petId, true);
                continue;
            }
            if (owner.isDead()) continue;
            PlayerPets.Progress pr = pp.get(petId);
            followAndFight(owner, pet, petId, pr);
            if (tick % 10 == 0) applyPassives(owner, petId, pr);
            periodicAbilities(owner, petId, pr);
        }
        if (lastHit.size() > 500) lastHit.clear();
    }

    private void tickTrails() {
        for (Map.Entry<UUID, Mob> en : active.entrySet()) {
            Mob pet = en.getValue();
            if (!pet.isValid()) continue;
            PlayerPets pp = data(en.getKey());
            if (pp.active == null || petSec(pp.active) == null) continue;
            Particle p = selectedParticle(pp.active, pp.get(pp.active));
            if (p == null) continue;
            pet.getWorld().spawnParticle(p, pet.getLocation().add(0, pet.getHeight() * 0.5, 0),
                    2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    // ------------------------------------------------------------- invocación

    public boolean isPet(Entity e) {
        return e.getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    public UUID petOwner(Entity e) {
        String s = e.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean hasActive(Player p) {
        Mob m = active.get(p.getUniqueId());
        return m != null && m.isValid();
    }

    public Component petName(String petId, PlayerPets.Progress pr) {
        return plugin.color(displayName(petId, pr) + " <dark_gray>[<yellow>Nv." + pr.level + "<dark_gray>]");
    }

    /** Nombre mostrado (MiniMessage): apodo del jugador o nombre de la config. */
    public String displayName(String petId, PlayerPets.Progress pr) {
        if (pr != null && pr.nickname != null && !pr.nickname.isBlank()) {
            return "<yellow>" + MiniMessage.miniMessage().escapeTags(pr.nickname);
        }
        return petSec(petId).getString("nombre", petId);
    }

    /** Bonus de daño acumulado respecto al nivel 1, en porcentaje. */
    public double bonusPercent(String petId, int level) {
        double base = damage(petId, 1);
        return base <= 0 ? 0 : (damage(petId, level) / base - 1) * 100;
    }

    // ---------------------------------------------------------------- renombrar

    public void startRename(Player p, String petId) {
        renaming.put(p.getUniqueId(), petId);
        plugin.msg(p, "<yellow>Escribe en el chat el nuevo nombre de tu mascota <gray>(máx. 16 letras).");
        plugin.msg(p, "<gray>Escribe <white>cancelar<gray> para salir o <white>quitar<gray> para restaurar el nombre original.");
    }

    public boolean isRenaming(UUID id) {
        return renaming.containsKey(id);
    }

    public void cancelRename(UUID id) {
        renaming.remove(id);
    }

    public void handleRename(Player p, String text) {
        String petId = renaming.remove(p.getUniqueId());
        if (petId == null || petSec(petId) == null) return;
        PlayerPets.Progress pr = data(p.getUniqueId()).get(petId);
        String t = text.trim();
        if (t.equalsIgnoreCase("cancelar")) {
            plugin.msg(p, "<gray>Renombrado cancelado.");
            return;
        }
        if (t.equalsIgnoreCase("quitar")) {
            pr.nickname = null;
            plugin.msg(p, "<green>Tu mascota recuperó su nombre original.");
        } else {
            t = t.replaceAll("[<>]", "").trim();
            if (t.length() > 16) t = t.substring(0, 16);
            if (t.isBlank()) {
                plugin.msg(p, "<red>Nombre inválido.");
                return;
            }
            pr.nickname = t;
            plugin.msg(p, "<green>Tu mascota ahora se llama <yellow>" + MiniMessage.miniMessage().escapeTags(t) + "<green>.");
        }
        updateName(p.getUniqueId());
    }

    public boolean spawn(Player owner, String petId, boolean silent) {
        ConfigurationSection sec = petSec(petId);
        PlayerPets pp = data(owner.getUniqueId());
        if (sec == null || !pp.unlocked.contains(petId)) return false;
        despawn(owner.getUniqueId());

        EntityType type;
        try {
            type = EntityType.valueOf(sec.getString("entidad", "WOLF").toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Entidad inválida en la mascota " + petId);
            return false;
        }
        Location loc = owner.getLocation();
        Entity ent = loc.getWorld().spawnEntity(loc, type);
        if (!(ent instanceof Mob mob)) {
            ent.remove();
            plugin.getLogger().warning("La entidad de " + petId + " no es un Mob, no se puede usar.");
            return false;
        }
        PlayerPets.Progress pr = pp.get(petId);
        mob.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        mob.setPersistent(false);
        mob.setInvulnerable(true);
        mob.setCanPickupItems(false);
        mob.setCollidable(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCustomNameVisible(true);
        mob.customName(petName(petId, pr));
        mob.setTarget(null);
        if (mob instanceof Ageable a) a.setAdult();
        if (mob instanceof Wolf w) w.setAngry(false);

        active.put(owner.getUniqueId(), mob);
        pp.active = petId;
        mob.getWorld().spawnParticle(Particle.CLOUD, mob.getLocation().add(0, 0.5, 0), 20, 0.4, 0.4, 0.4, 0.02);
        if (!silent) plugin.msg(owner, "<green>Has invocado a tu mascota: " + sec.getString("nombre", petId));
        return true;
    }

    public void despawn(UUID owner) {
        Mob m = active.remove(owner);
        if (m != null) m.remove();
        targets.remove(owner);
    }

    public void dismiss(Player p) {
        PlayerPets pp = data(p.getUniqueId());
        despawn(p.getUniqueId());
        pp.active = null;
        plugin.msg(p, "<gray>Has guardado a tu mascota.");
    }

    public void updateName(UUID owner) {
        Mob m = active.get(owner);
        PlayerPets pp = data(owner);
        if (m != null && m.isValid() && pp.active != null && petSec(pp.active) != null) {
            m.customName(petName(pp.active, pp.get(pp.active)));
        }
    }

    // ----------------------------------------------------------------- combate

    public void setTarget(Player owner, LivingEntity target) {
        if (!active.containsKey(owner.getUniqueId())) return;
        if (target.equals(owner) || isPet(target) || !target.isValid()) return;
        if (target instanceof Player && !cfg().getBoolean("mascota.atacar-jugadores", false)) return;
        targets.put(owner.getUniqueId(), target);
    }

    public UUID takeLastHit(UUID target) {
        return lastHit.remove(target);
    }

    private LivingEntity findTarget(Player owner) {
        UUID id = owner.getUniqueId();
        LivingEntity t = targets.get(id);
        double range = cfg().getDouble("mascota.rango-ataque", 12);
        if (t != null && (!t.isValid() || t.isDead() || !t.getWorld().equals(owner.getWorld())
                || t.getLocation().distanceSquared(owner.getLocation()) > range * range)) {
            targets.remove(id);
            t = null;
        }
        if (t == null && cfg().getBoolean("mascota.auto-defender", true)) {
            double r = cfg().getDouble("mascota.rango-defensa", 8);
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (Entity e : owner.getNearbyEntities(r, r, r)) {
                if (!(e instanceof Enemy) || !(e instanceof LivingEntity le)) continue;
                if (isPet(e) || !e.isValid()) continue;
                double d = e.getLocation().distanceSquared(owner.getLocation());
                if (d < bd) {
                    bd = d;
                    best = le;
                }
            }
            t = best;
            if (t != null) targets.put(id, t);
        }
        return t;
    }

    private void followAndFight(Player owner, Mob pet, String petId, PlayerPets.Progress pr) {
        Location ol = owner.getLocation();
        double tp = cfg().getDouble("mascota.distancia-teletransporte", 20);
        if (!pet.getWorld().equals(ol.getWorld()) || pet.getLocation().distanceSquared(ol) > tp * tp) {
            pet.teleport(ol);
            targets.remove(owner.getUniqueId());
            return;
        }
        pet.setTarget(null);
        double speed = cfg().getDouble("mascota.velocidad", 1.2);
        LivingEntity target = findTarget(owner);
        if (target != null) {
            pet.getPathfinder().moveTo(target.getLocation(), speed);
            if (pet.getBoundingBox().expand(1.0).overlaps(target.getBoundingBox())) {
                attack(owner, pet, petId, pr, target);
            }
        } else {
            double follow = cfg().getDouble("mascota.distancia-seguir", 3.0);
            if (pet.getLocation().distanceSquared(ol) > follow * follow) {
                pet.getPathfinder().moveTo(ol, speed);
            } else {
                pet.getPathfinder().stopPathfinding();
            }
        }
    }

    private void attack(Player owner, Mob pet, String petId, PlayerPets.Progress pr, LivingEntity target) {
        long now = System.currentTimeMillis();
        long cd = (long) (cfg().getDouble("mascota.enfriamiento-ataque", 1.0) * 1000);
        if (now - attackCd.getOrDefault(owner.getUniqueId(), 0L) < cd) return;
        attackCd.put(owner.getUniqueId(), now);

        lastHit.put(target.getUniqueId(), owner.getUniqueId());
        pet.swingMainHand();
        target.damage(damage(petId, pr.level), pet);
        applyHitAbilities(pet, target, petId, pr.level);
    }

    private void applyHitAbilities(Mob pet, LivingEntity target, String petId, int level) {
        for (Map<?, ?> m : petSec(petId).getMapList("habilidades")) {
            String tipo = String.valueOf(m.get("tipo")).toUpperCase();
            if (level < num(m, "nivel", 1)) continue;
            int ticks = (int) (dbl(m, "duracion", 4) * 20);
            switch (tipo) {
                case "FUEGO" -> target.setFireTicks(ticks);
                case "RAYO" -> {
                    if (ThreadLocalRandom.current().nextDouble() < dbl(m, "probabilidad", 0.2)) {
                        target.getWorld().strikeLightningEffect(target.getLocation());
                        target.damage(dbl(m, "danio", 4), pet);
                    }
                }
                case "LENTITUD" -> target.addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, ticks, num(m, "nivel-efecto", 1)));
                case "VENENO" -> target.addPotionEffect(
                        new PotionEffect(PotionEffectType.POISON, ticks, num(m, "nivel-efecto", 0)));
                default -> { }
            }
        }
    }

    private void periodicAbilities(Player owner, String petId, PlayerPets.Progress pr) {
        long now = System.currentTimeMillis();
        for (Map<?, ?> m : petSec(petId).getMapList("habilidades")) {
            String tipo = String.valueOf(m.get("tipo")).toUpperCase();
            if (!tipo.equals("CURAR") && !tipo.equals("ESCUDO")) continue;
            if (pr.level < num(m, "nivel", 1)) continue;
            String key = owner.getUniqueId() + ":" + petId + ":" + tipo;
            if (now < cooldowns.getOrDefault(key, 0L)) continue;

            boolean fired = false;
            if (tipo.equals("CURAR")) {
                var attr = owner.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                if (owner.getHealth() < max * 0.7) {
                    owner.setHealth(Math.min(max, owner.getHealth() + dbl(m, "cantidad", 4)));
                    owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 1.8, 0),
                            6, 0.4, 0.3, 0.4, 0);
                    owner.sendActionBar(plugin.color("<red>Tu mascota te ha curado"));
                    fired = true;
                }
            } else if (targets.containsKey(owner.getUniqueId())
                    && !owner.hasPotionEffect(PotionEffectType.ABSORPTION)) {
                owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,
                        (int) (dbl(m, "duracion", 15) * 20), num(m, "nivel-efecto", 1)));
                owner.sendActionBar(plugin.color("<yellow>Tu mascota te protege con un escudo"));
                fired = true;
            }
            if (fired) cooldowns.put(key, now + (long) (dbl(m, "enfriamiento", 30) * 1000));
        }
    }

    private void applyPassives(Player owner, String petId, PlayerPets.Progress pr) {
        for (String s : petSec(petId).getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length < 3) continue;
            if (pr.level < parseInt(p[2], 1)) continue;
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.minecraft(p[0].trim().toLowerCase()));
            if (t == null) continue;
            owner.addPotionEffect(new PotionEffect(t, 400, parseInt(p[1], 0), true, false, true));
        }
    }

    // ------------------------------------------------------------------ estelas

    public List<String> unlockedParticles(String petId, int level) {
        List<String> out = new ArrayList<>();
        for (Map<?, ?> m : petSec(petId).getMapList("estela")) {
            if (level >= num(m, "nivel", 1)) out.add(String.valueOf(m.get("particula")).toUpperCase());
        }
        return out;
    }

    private Particle selectedParticle(String petId, PlayerPets.Progress pr) {
        List<String> unlocked = unlockedParticles(petId, pr.level);
        if (unlocked.isEmpty() || pr.particle.equalsIgnoreCase("none")) return null;
        String name = pr.particle.equalsIgnoreCase("auto") || !unlocked.contains(pr.particle.toUpperCase())
                ? unlocked.get(unlocked.size() - 1)
                : pr.particle.toUpperCase();
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void cycleParticle(Player p, String petId) {
        PlayerPets.Progress pr = data(p.getUniqueId()).get(petId);
        List<String> options = new ArrayList<>();
        options.add("auto");
        options.addAll(unlockedParticles(petId, pr.level));
        options.add("none");
        int idx = options.indexOf(pr.particle.equalsIgnoreCase("auto") || pr.particle.equalsIgnoreCase("none")
                ? pr.particle.toLowerCase() : pr.particle.toUpperCase());
        pr.particle = options.get((idx + 1) % options.size());
    }

    // ---------------------------------------------------------------- niveles

    public void awardKill(Player owner, LivingEntity dead, boolean byPet) {
        PlayerPets pp = data(owner.getUniqueId());
        if (pp.active == null || !active.containsKey(owner.getUniqueId())) return;
        double xp;
        if (dead instanceof Boss) xp = cfg().getDouble("xp.por-matar-jefe", 500);
        else if (dead instanceof Player) xp = cfg().getDouble("xp.por-matar-jugador", 25);
        else if (dead instanceof Enemy) xp = cfg().getDouble("xp.por-matar-hostil", 10);
        else xp = cfg().getDouble("xp.por-matar-pasivo", 2);
        if (byPet) xp += cfg().getDouble("xp.bonus-ataque-mascota", 5);
        pp.get(pp.active).kills++;
        double gained = xp * cfg().getDouble("xp.multiplicador-global", 1.0);
        addXp(owner, pp.active, xp);
        PlayerPets.Progress pr = pp.get(pp.active);
        if (pr.level < maxLevel()) {
            owner.sendActionBar(plugin.color("<green>+" + String.format(Locale.US, "%.1f", gained)
                    + " XP <gray>» " + petSec(pp.active).getString("nombre", pp.active)));
        }
    }

    public void addXp(Player owner, String petId, double amount) {
        PlayerPets pp = data(owner.getUniqueId());
        if (petSec(petId) == null || !pp.unlocked.contains(petId)) return;
        PlayerPets.Progress pr = pp.get(petId);
        if (pr.level >= maxLevel()) return;
        pr.xp += amount * cfg().getDouble("xp.multiplicador-global", 1.0);
        while (pr.level < maxLevel() && pr.xp >= xpNeeded(pr.level)) {
            pr.xp -= xpNeeded(pr.level);
            pr.level++;
            levelUp(owner, petId, pr);
        }
        if (pr.level >= maxLevel()) pr.xp = 0;
    }

    public void setLevel(Player owner, String petId, int target) {
        PlayerPets pp = data(owner.getUniqueId());
        if (petSec(petId) == null) return;
        pp.unlocked.add(petId);
        PlayerPets.Progress pr = pp.get(petId);
        target = Math.max(1, Math.min(maxLevel(), target));
        pr.xp = 0;
        if (target < pr.level) {
            pr.level = target;
            updateName(owner.getUniqueId());
            return;
        }
        while (pr.level < target) {
            pr.level++;
            levelUp(owner, petId, pr);
        }
    }

    public boolean unlock(Player owner, String petId) {
        if (petSec(petId) == null) return false;
        return data(owner.getUniqueId()).unlocked.add(petId);
    }

    private void levelUp(Player owner, String petId, PlayerPets.Progress pr) {
        ConfigurationSection sec = petSec(petId);
        String name = sec.getString("nombre", petId);
        updateName(owner.getUniqueId());

        owner.showTitle(Title.title(
                plugin.color("<gold><bold>¡NIVEL " + pr.level + "!"),
                plugin.color(name),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Mob pet = active.get(owner.getUniqueId());
        if (pet != null && pet.isValid()) {
            pet.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, pet.getLocation().add(0, 1, 0),
                    40, 0.5, 0.8, 0.5, 0.2);
        }

        for (Map<?, ?> m : sec.getMapList("habilidades")) {
            if (num(m, "nivel", 1) == pr.level) {
                plugin.msg(owner, "<light_purple>Nueva habilidad desbloqueada: <white>"
                        + abilityName(String.valueOf(m.get("tipo"))));
            }
        }
        for (String s : sec.getStringList("efectos")) {
            String[] p = s.split(":");
            if (p.length >= 3 && parseInt(p[2], 1) == pr.level) {
                plugin.msg(owner, "<aqua>Nuevo efecto pasivo: <white>" + p[0].toLowerCase().replace('_', ' ')
                        + " " + (parseInt(p[1], 0) + 1));
            }
        }
        for (Map<?, ?> m : sec.getMapList("estela")) {
            if (num(m, "nivel", 1) == pr.level) {
                plugin.msg(owner, "<yellow>Nueva estela de partículas desbloqueada: <white>"
                        + String.valueOf(m.get("particula")).toLowerCase().replace('_', ' '));
            }
        }

        if (pr.claimed.add(pr.level)) grantReward(owner, petId, pr.level);
    }

    private void grantReward(Player owner, String petId, int level) {
        ConfigurationSection r = cfg().getConfigurationSection("recompensas." + level);
        if (r == null) return;
        String only = r.getString("solo-mascota");
        if (only != null && !only.equalsIgnoreCase(petId)) return;

        String msg = r.getString("mensaje");
        if (msg != null) plugin.msg(owner, msg);

        String unlock = r.getString("desbloquear-mascota");
        if (unlock != null && petSec(unlock) != null && data(owner.getUniqueId()).unlocked.add(unlock)) {
            plugin.msg(owner, "<green>¡Has desbloqueado una nueva mascota: "
                    + petSec(unlock).getString("nombre", unlock) + "<green>!");
            owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        for (String cmd : r.getStringList("comandos")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd
                    .replace("%player%", owner.getName())
                    .replace("%pet%", petId)
                    .replace("%level%", String.valueOf(level)));
        }
    }
}
